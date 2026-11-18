package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.license.data.LicenseVersion;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.LicenseRepository;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * LicenseService 단위 테스트.
 * 핵심 검증: 테넌트별 캐시 격리, 캐시 적중/만료/갱신 분기, register 후 invalidate, 방어 복사.
 * 실 라이선스 blob 파싱(LicenseManager) 경로는 외부 모듈 의존이라 reflection 으로 CacheEntry 를
 * 직접 주입해 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LicenseService 단위 테스트")
class LicenseServiceTest {

    private static final int TENANT_A = 1;
    private static final int TENANT_B = 2;

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private LicenseQueryRepository licenseQueryRepository;

    @Mock
    private LicenseAuthService licenseAuthService;

    @InjectMocks
    private LicenseService licenseService;

    private ConcurrentMap<Integer, LicenseService.CacheEntry> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cache = (ConcurrentMap<Integer, LicenseService.CacheEntry>) ReflectionTestUtils.getField(licenseService, "cacheByTenant");
    }

    private Map<String, Object> fakeLicenseValue(Instant updatedAt) {
        Map<String, Object> v = new HashMap<>();
        v.put(Constants.LICENSE.UPDATED_AT, updatedAt);
        v.put(Constants.LICENSE.STATE, true);
        v.put(Constants.LICENSE.DAYS, 365);
        v.put(Constants.LICENSE.SPENTS, 30);
        v.put(Constants.LICENSE.EXPIRES_ON, "2026-12-31");
        v.put(Constants.LICENSE.PAID_WHITE_LABEL, Boolean.FALSE);
        v.put(LicenseInfos.LICENSE_TYPE, Constants.LICENSE.LICENSE_TYPE_SEAT);
        v.put(LicenseInfos.CONSTRAIN_VALUE, "1000");
        return v;
    }

    private LicenseService.CacheEntry entryOf(Map<String, Object> license, String lastRegDate) {
        Instant updatedAt = (Instant) license.get(Constants.LICENSE.UPDATED_AT);
        return new LicenseService.CacheEntry(license, lastRegDate, new LicenseVersion(updatedAt, null));
    }

    /** LicenseService 의 lastRegDate 포맷(yyyy-MM-dd) 과 동일한 오늘 문자열 — 캐시 hit 시나리오 setup 용. */
    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    @Nested
    @DisplayName("getLicense() - 캐시/DB 동기화")
    class GetLicense {

        @Test
        @DisplayName("DB 에 라이선스가 없으면 → null 반환 + 해당 테넌트 캐시 제거 (원자적)")
        void dbHasNoLicense_returnsNullAndClearsCache() throws Exception {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-01-01"));
            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(null);

            Map result = licenseService.getLicense(TENANT_A);

            assertThat(result).isNull();
            assertThat(cache).doesNotContainKey(TENANT_A);
        }

        @Test
        @DisplayName("캐시가 최신 + 같은 날 → DB selectLicense 호출 없이 cached license 반환")
        void cachedAndFreshSameDay_returnsCachedWithoutRefetch() throws Exception {
            Instant updatedAt = Instant.now();
            Map<String, Object> licenseMap = fakeLicenseValue(updatedAt);
            String today = today();
            cache.put(TENANT_A, entryOf(licenseMap, today));

            // check 3 통과(cache UPDATED_AT == DB updatedAt) + 같은 날 → fetchAndSetLicense 진입 안 함
            // → selectLicense 는 호출되지 않으므로 stub 불필요
            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(new LicenseVersion(updatedAt, null));
            // 오늘 날짜의 DB 시각 — 미스텁이면 0L(1970-01-01)이라 날짜가 바뀐 것으로 보고 캐시 재계산 경로로 빠진다
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            Map result = licenseService.getLicense(TENANT_A);

            assertThat(result).isSameAs(licenseMap);
            then(licenseQueryRepository).should(never()).findLicense(TENANT_A);
        }

        @Test
        @DisplayName("테넌트 격리 - 테넌트 A 캐시가 있어도 테넌트 B 조회는 독립적으로 동작 + A 캐시 무영향")
        void perTenantIsolation_tenantBLookupDoesNotTouchTenantACache() throws Exception {
            Map<String, Object> licenseMapA = fakeLicenseValue(Instant.now());
            cache.put(TENANT_A, entryOf(licenseMapA, "2026-05-20"));

            given(licenseQueryRepository.findLicenseVersion(TENANT_B)).willReturn(null);

            Map result = licenseService.getLicense(TENANT_B);

            assertThat(result).isNull();
            assertThat(cache).containsKey(TENANT_A);
            assertThat(cache.get(TENANT_A).license()).isSameAs(licenseMapA);
        }
    }

    @Nested
    @DisplayName("isPaidWhiteLabel(tenantSeq)")
    class IsPaidWhiteLabel {

        @Test
        @DisplayName("캐시 없음 → false")
        void cacheMiss_returnsFalse() {
            assertThat(licenseService.isPaidWhiteLabel(TENANT_A)).isFalse();
        }

        @Test
        @DisplayName("캐시에 paidWhiteLabel=true → true")
        void cacheHitTrue_returnsTrue() {
            Map<String, Object> v = fakeLicenseValue(Instant.now());
            v.put(Constants.LICENSE.PAID_WHITE_LABEL, Boolean.TRUE);
            cache.put(TENANT_A, entryOf(v, "2026-05-20"));

            assertThat(licenseService.isPaidWhiteLabel(TENANT_A)).isTrue();
        }

        @Test
        @DisplayName("캐시에 paidWhiteLabel=false → false")
        void cacheHitFalse_returnsFalse() {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));

            assertThat(licenseService.isPaidWhiteLabel(TENANT_A)).isFalse();
        }

        @Test
        @DisplayName("테넌트 격리 - 테넌트 A 만 true 일 때 테넌트 B 는 false")
        void perTenantIsolation() {
            Map<String, Object> vA = fakeLicenseValue(Instant.now());
            vA.put(Constants.LICENSE.PAID_WHITE_LABEL, Boolean.TRUE);
            cache.put(TENANT_A, entryOf(vA, "2026-05-20"));

            assertThat(licenseService.isPaidWhiteLabel(TENANT_A)).isTrue();
            assertThat(licenseService.isPaidWhiteLabel(TENANT_B)).isFalse();
        }
    }

    @Nested
    @DisplayName("getConstrainValue(tenantSeq)")
    class GetConstrainValue {

        @Test
        @DisplayName("캐시 없음 → 0")
        void cacheMiss_returnsZero() {
            assertThat(licenseService.getConstrainValue(TENANT_A)).isEqualTo(0);
        }

        @Test
        @DisplayName("캐시의 CONSTRAIN_VALUE 문자열 → int 파싱")
        void cacheHit_returnsParsedInt() {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));

            assertThat(licenseService.getConstrainValue(TENANT_A)).isEqualTo(1000);
        }

        @Test
        @DisplayName("테넌트 격리 - 각 테넌트의 캐시값을 독립적으로 반환")
        void perTenantIsolation() {
            Map<String, Object> vA = fakeLicenseValue(Instant.now());
            vA.put(LicenseInfos.CONSTRAIN_VALUE, "100");
            Map<String, Object> vB = fakeLicenseValue(Instant.now());
            vB.put(LicenseInfos.CONSTRAIN_VALUE, "500");
            cache.put(TENANT_A, entryOf(vA, "2026-05-20"));
            cache.put(TENANT_B, entryOf(vB, "2026-05-20"));

            assertThat(licenseService.getConstrainValue(TENANT_A)).isEqualTo(100);
            assertThat(licenseService.getConstrainValue(TENANT_B)).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("getLicenseStateMap(tenantSeq)")
    class GetLicenseStateMap {

        @Test
        @DisplayName("정상 캐시 → state/days/spents/expiresOn/paidWhiteLabel 채워서 반환")
        void normalCache_returnsAllFields() throws Exception {
            Instant updatedAt = Instant.now();
            Map<String, Object> v = fakeLicenseValue(updatedAt);
            v.put(Constants.LICENSE.DAYS, 365);
            v.put(Constants.LICENSE.SPENTS, 100);
            String today = today();
            cache.put(TENANT_A, entryOf(v, today));

            // 캐시 hit + 같은 날 → getLicense 내부에서 selectLicense 호출 없음
            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(new LicenseVersion(updatedAt, null));
            // 오늘 날짜의 DB 시각 — 미스텁이면 0L(1970-01-01)이라 날짜가 바뀐 것으로 보고 캐시 재계산 경로로 빠진다
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            Map result = licenseService.getLicenseStateMap(TENANT_A);

            assertThat(result.get(Constants.LICENSE.STATE)).isEqualTo(true);
            assertThat(result.get(Constants.LICENSE.DAYS)).isEqualTo(365);
            assertThat(result.get(Constants.LICENSE.SPENTS)).isEqualTo(100);
            assertThat(result.get(Constants.LICENSE.EXPIRES_ON)).isEqualTo("2026-12-31");
            assertThat(result.get(Constants.LICENSE.PAID_WHITE_LABEL)).isEqualTo(false);
        }

        @Test
        @DisplayName("만료 10일 이내 → LICENSE_UNDER_LIMIT 코드 포함")
        void underTenDays_includesUnderLimitCode() throws Exception {
            Instant updatedAt = Instant.now();
            Map<String, Object> v = fakeLicenseValue(updatedAt);
            v.put(Constants.LICENSE.DAYS, 365);
            v.put(Constants.LICENSE.SPENTS, 360);
            String today = today();
            cache.put(TENANT_A, entryOf(v, today));

            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(new LicenseVersion(updatedAt, null));
            // 오늘 날짜의 DB 시각 — 미스텁이면 0L(1970-01-01)이라 날짜가 바뀐 것으로 보고 캐시 재계산 경로로 빠진다
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            Map result = licenseService.getLicenseStateMap(TENANT_A);

            assertThat(result).containsKey("code");
            assertThat(result.get("code")).isEqualTo(6994);
        }

        @Test
        @DisplayName("만료(spents > days) → LICENSE_FILE_EXPIRATION 코드 포함")
        void afterExpiry_includesExpirationCode() throws Exception {
            Instant updatedAt = Instant.now();
            Map<String, Object> v = fakeLicenseValue(updatedAt);
            v.put(Constants.LICENSE.DAYS, 100);
            v.put(Constants.LICENSE.SPENTS, 110);
            String today = today();
            cache.put(TENANT_A, entryOf(v, today));

            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(new LicenseVersion(updatedAt, null));
            // 오늘 날짜의 DB 시각 — 미스텁이면 0L(1970-01-01)이라 날짜가 바뀐 것으로 보고 캐시 재계산 경로로 빠진다
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            Map result = licenseService.getLicenseStateMap(TENANT_A);

            assertThat(result.get("code")).isEqualTo(6995);
        }
    }

    @Nested
    @DisplayName("convertLicenseForDisplay() - 방어 복사 검증 포함")
    class ConvertLicenseForDisplay {

        @Test
        @DisplayName("SEAT 라이선스 → currentSeatNum, availableSeatNum 추가")
        void seatLicense_addsSeatFields() {
            Map<String, Object> license = new HashMap<>();
            license.put(LicenseInfos.LICENSE_TYPE, Constants.LICENSE.LICENSE_TYPE_SEAT);
            license.put(LicenseInfos.CONSTRAIN_VALUE, "1000");

            Map<String, Object> result = licenseService.convertLicenseForDisplay(license, 200);

            assertThat(result.get("currentSeatNum")).isEqualTo(200);
            assertThat(result.get("availableSeatNum")).isEqualTo(800);
        }

        @Test
        @DisplayName("non-SEAT 라이선스 → 시트 필드 추가 안 함")
        void nonSeatLicense_doesNotAddSeatFields() {
            Map<String, Object> license = new HashMap<>();
            license.put(LicenseInfos.LICENSE_TYPE, "SITE");

            Map<String, Object> result = licenseService.convertLicenseForDisplay(license, 200);

            assertThat(result).doesNotContainKey("currentSeatNum");
            assertThat(result).doesNotContainKey("availableSeatNum");
        }

        @Test
        @DisplayName("입력 Map 은 mutate 되지 않음 (방어 복사 → 캐시 인스턴스 안전)")
        void doesNotMutateInputMap() {
            Map<String, Object> license = new HashMap<>();
            license.put(LicenseInfos.LICENSE_TYPE, Constants.LICENSE.LICENSE_TYPE_SEAT);
            license.put(LicenseInfos.CONSTRAIN_VALUE, "1000");

            licenseService.convertLicenseForDisplay(license, 200);

            assertThat(license).doesNotContainKey("currentSeatNum");
            assertThat(license).doesNotContainKey("availableSeatNum");
        }

        @Test
        @DisplayName("반환 Map 은 입력과 다른 인스턴스")
        void returnsNewInstance() {
            Map<String, Object> license = new HashMap<>();
            license.put(LicenseInfos.LICENSE_TYPE, "SITE");

            Map<String, Object> result = licenseService.convertLicenseForDisplay(license, 0);

            assertThat(result).isNotSameAs(license);
        }
    }

    @Nested
    @DisplayName("isSeatLicense(tenantSeq)")
    class IsSeatLicense {

        @Test
        @DisplayName("캐시 없음 → false")
        void cacheMiss_returnsFalse() {
            assertThat(licenseService.isSeatLicense(TENANT_A)).isFalse();
        }

        @Test
        @DisplayName("SEAT 라이선스 캐시 → true")
        void cacheHasSeat_returnsTrue() {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));

            assertThat(licenseService.isSeatLicense(TENANT_A)).isTrue();
        }

        @Test
        @DisplayName("다른 타입(SITE 등) 캐시 → false")
        void cacheHasOtherType_returnsFalse() {
            Map<String, Object> v = fakeLicenseValue(Instant.now());
            v.put(LicenseInfos.LICENSE_TYPE, "SITE");
            cache.put(TENANT_A, entryOf(v, "2026-05-20"));

            assertThat(licenseService.isSeatLicense(TENANT_A)).isFalse();
        }

        @Test
        @DisplayName("테넌트 격리 - A 만 SEAT, B 는 캐시 없음 → 각각 true/false")
        void perTenantIsolation() {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));

            assertThat(licenseService.isSeatLicense(TENANT_A)).isTrue();
            assertThat(licenseService.isSeatLicense(TENANT_B)).isFalse();
        }
    }

    @Nested
    @DisplayName("getSeatPolicy(tenantSeq) - SQL atomic check 용 스냅샷")
    class GetSeatPolicy {

        @Test
        @DisplayName("캐시 없음 → SeatPolicy(false, 0)")
        void cacheMiss_returnsEmptyPolicy() {
            var policy = licenseService.getSeatPolicy(TENANT_A);

            assertThat(policy.isSeat()).isFalse();
            assertThat(policy.constrainValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("SEAT + constrain=1000 → SeatPolicy(true, 1000)")
        void seatCache_returnsFullPolicy() {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));

            var policy = licenseService.getSeatPolicy(TENANT_A);

            assertThat(policy.isSeat()).isTrue();
            assertThat(policy.constrainValue()).isEqualTo(1000);
        }

        @Test
        @DisplayName("비-SEAT 라이선스 → constrainValue=0 (parse 시도 안 함)")
        void nonSeatCache_constrainIsZero() {
            Map<String, Object> v = fakeLicenseValue(Instant.now());
            v.put(LicenseInfos.LICENSE_TYPE, "SITE");
            v.remove(LicenseInfos.CONSTRAIN_VALUE);  // SITE 라이선스는 constrain 키 자체가 없을 수 있음
            cache.put(TENANT_A, entryOf(v, "2026-05-20"));

            var policy = licenseService.getSeatPolicy(TENANT_A);

            assertThat(policy.isSeat()).isFalse();
            assertThat(policy.constrainValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("테넌트 격리 - 각 테넌트의 policy 가 독립적")
        void perTenantIsolation() {
            Map<String, Object> vA = fakeLicenseValue(Instant.now());
            vA.put(LicenseInfos.CONSTRAIN_VALUE, "100");
            cache.put(TENANT_A, entryOf(vA, "2026-05-20"));

            Map<String, Object> vB = fakeLicenseValue(Instant.now());
            vB.put(LicenseInfos.CONSTRAIN_VALUE, "500");
            cache.put(TENANT_B, entryOf(vB, "2026-05-20"));

            assertThat(licenseService.getSeatPolicy(TENANT_A).constrainValue()).isEqualTo(100);
            assertThat(licenseService.getSeatPolicy(TENANT_B).constrainValue()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("registerLicense(tenantSeq, blob)")
    class RegisterLicense {

        @Test
        @DisplayName("유효하지 않은 blob → 형식 오류(6997), mapper 호출 없음")
        void invalidBlob_throwsInvalidFormat_doesNotCallRepository() {
            byte[] invalidBlob = new byte[]{1, 2, 3};

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT_A, invalidBlob))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT);

            then(licenseRepository).should(never()).replaceLicense(org.mockito.ArgumentMatchers.anyInt(),
                                                               org.mockito.ArgumentMatchers.any(),
                                                               org.mockito.ArgumentMatchers.any(),
                                                               org.mockito.ArgumentMatchers.anyBoolean());
            // 빌드 실패 → 캐시도 그대로 (제거되지 않음)
            assertThat(cache).doesNotContainKey(TENANT_A);
        }
    }

    @Nested
    @DisplayName("캐시 무효화 동작")
    class CacheInvalidation {

        @Test
        @DisplayName("getLicense 에서 DB updatedAt null → 캐시에서 해당 테넌트 entry 한 번에 제거")
        void getLicense_dbHasNoRow_removesEntireEntry() throws Exception {
            cache.put(TENANT_A, entryOf(fakeLicenseValue(Instant.now()), "2026-05-20"));
            assertThat(cache).containsKey(TENANT_A);

            given(licenseQueryRepository.findLicenseVersion(TENANT_A)).willReturn(null);

            licenseService.getLicense(TENANT_A);

            // license + lastRegDate 가 단일 entry 라 부분 잔존 없음
            assertThat(cache).doesNotContainKey(TENANT_A);
        }
    }
}
