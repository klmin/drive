package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.hancom.weboffice.common.license.v2.LicenseClaims;
import com.hancom.weboffice.common.license.v2.LicenseV2Signer;
import com.hancom.weboffice.common.license.v2.SitePublicKeyProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.auth.enums.LicenseAuthStatus;
import com.thinkfree.drive.license.auth.result.LicenseAuthResult;
import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseVersion;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.repository.LicenseRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * RSA 서명 라이선스("TR")의 등록·로드 검증.
 * 테스트용 키쌍을 만들어 공개키를 신뢰 키로 지정하고, 같은 개인키로 서명한 파일을 넣어 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LicenseService RSA 라이선스 검증")
class LicenseServiceRsaTest {

    private static final int TENANT = 1;
    private static final String AUTH_URL = "https://auth.example/api/public/v1";

    @TempDir
    static Path tempDir;

    private static RSAPrivateKey trustedKey;
    private static RSAPrivateKey untrustedKey;

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private LicenseQueryRepository licenseQueryRepository;

    @Mock
    private LicenseAuthService licenseAuthService;

    @InjectMocks
    private LicenseService licenseService;

    @BeforeAll
    static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair trusted = generator.generateKeyPair();
        trustedKey = (RSAPrivateKey) trusted.getPrivate();
        untrustedKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

        Path publicKeyFile = tempDir.resolve("thinkfree.public.key");
        Files.writeString(publicKeyFile, Base64.getEncoder().encodeToString(trusted.getPublic().getEncoded()));
        System.setProperty(SitePublicKeyProvider.PATH_PROPERTY, publicKeyFile.toString());
        SitePublicKeyProvider.reset();
    }

    @AfterAll
    static void tearDownKeys() {
        System.clearProperty(SitePublicKeyProvider.PATH_PROPERTY);
        SitePublicKeyProvider.reset();
    }

    private static byte[] sign(String productName, RSAPrivateKey key) throws Exception {
        return sign(productName, key, "seat::100");
    }

    private static byte[] signOnline() throws Exception {
        return signOnline(100L);
    }

    private static byte[] signOnline(long licenseId) throws Exception {
        return sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey, "seat::100,auth::" + AUTH_URL + ",stats", licenseId);
    }

    private static byte[] sign(String productName, RSAPrivateKey key, String addFlag) throws Exception {
        return sign(productName, key, addFlag, 100L);
    }

    private static byte[] sign(String productName, RSAPrivateKey key, String addFlag, long licenseId) throws Exception {
        Date now = new Date();
        LicenseClaims claims = LicenseClaims.builder()
                .licenseId(licenseId)
                .category("PRODUCTION")
                .licenseType("SEAT")
                .productName(productName)
                .clientName("Thinkfree")
                .userId("tester")
                .issuedAt(Date.from(now.toInstant().minus(10, ChronoUnit.DAYS)))
                .expiresAt(Date.from(now.toInstant().plus(365, ChronoUnit.DAYS)))
                .addFlag(addFlag)
                .build();
        return LicenseV2Signer.sign(claims, key, null);
    }

    /** 인증 서버가 서명해 준 원장 정보. 저장된 값은 서명을 이미 확인했다는 전제라 아무 키로 서명한다. */
    private static String signedLedger(String subject) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().subject(subject).build());
        jwt.sign(new RSASSASigner(untrustedKey));
        return jwt.serialize();
    }

    private static LicenseAuthResult authResult(LicenseAuthStatus status, String errorCode) {
        return new LicenseAuthResult(status, status == LicenseAuthStatus.GRACE_PERIOD ? 1 : 0,
                Instant.now(), null, null, null, errorCode);
    }

    private static LicenseAuthStateData authState(LicenseAuthStatus status, String signedData) {
        return new LicenseAuthStateData(null, status.name(), 0, Instant.now(), null,
                DriveTime.toInstant(LocalDateTime.of(2026, 10, 13, 3, 10, 0)), signedData, Instant.now());
    }

    /** 이미 등록돼 있는 온라인 라이선스의 저장 상태 */
    private static LicenseAuthStateData savedAuthState(byte[] blob, LicenseAuthStatus status, int failCount) {
        return new LicenseAuthStateData(blob, status.name(), failCount, Instant.now(), null, null, null, Instant.now());
    }

    /** 화면에는 모두 6997 로 나가므로 원인은 예외에 담긴 reason 으로 구분한다. */
    private static void assertRejected(ThrowingCallable call, String reason) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT);
                    assertThat(e.getContext()).containsEntry("reason", reason);
                });
    }

    private static byte[] legacyAesBlob() {
        return "TF-legacy-aes-license".getBytes(StandardCharsets.US_ASCII);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Integer, LicenseService.CacheEntry> cache() {
        return (ConcurrentMap<Integer, LicenseService.CacheEntry>) ReflectionTestUtils.getField(licenseService, "cacheByTenant");
    }

    private static Map<String, Object> dbRow(byte[] blob, Instant updatedAt) {
        Map<String, Object> row = new HashMap<>();
        row.put(Constants.DB.FIELD.DATA, blob);
        row.put(Constants.DB.FIELD.UPDATED_AT, updatedAt);
        return row;
    }

    @Nested
    @DisplayName("registerLicense()")
    class Register {

        @Test
        @DisplayName("AES 방식(TF) → 6997, 저장 안 함")
        void legacyAes_rejected() {
            assertRejected(() -> licenseService.registerLicense(TENANT, legacyAesBlob()), "not RSA license");

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("신뢰하지 않는 키로 서명 → 6997, 저장 안 함")
        void untrustedSignature_rejected() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, untrustedKey);

            assertRejected(() -> licenseService.registerLicense(TENANT, blob), "load failed");

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("다른 제품(webOffice) 라이선스 → 6997, 저장 안 함")
        void otherProduct_rejected() throws Exception {
            byte[] blob = sign("webOffice", trustedKey);

            assertRejected(() -> licenseService.registerLicense(TENANT, blob), "product mismatch");

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Drive 라이선스 → 저장")
        void driveLicense_saved() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseRepository.replaceLicense(TENANT, blob, null, false)).willReturn(1);

            licenseService.registerLicense(TENANT, blob);

            then(licenseRepository).should().replaceLicense(TENANT, blob, null, false);
        }

        @Test
        @DisplayName("저장 직후 캐시를 DB 값으로 채운다 → 캐시만 읽는 plan 조회도 바로 반영")
        void driveLicense_populatesCache() throws Exception {
            Instant updatedAt = Instant.now();
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseRepository.replaceLicense(TENANT, blob, null, false)).willReturn(1);
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(updatedAt, null));
            given(licenseQueryRepository.findLicense(TENANT)).willReturn(dbRow(blob, updatedAt));
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            licenseService.registerLicense(TENANT, blob);

            assertThat(licenseService.getLicensePlan(TENANT).category()).isEqualTo("PRODUCTION");
            assertThat(licenseService.getSeatPolicy(TENANT)).isEqualTo(new LicenseService.SeatPolicy(true, 100));
        }

        @Test
        @DisplayName("저장 후 캐시 적재 실패 → 이전 캐시를 비운다")
        void reloadFails_clearsPreviousCache() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseRepository.replaceLicense(TENANT, blob, null, false)).willReturn(1);
            // findLicense 미스텁 → null → LICENSE_FILE_NOT_EXIST
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(Instant.now(), null));
            ConcurrentMap<Integer, LicenseService.CacheEntry> cache = cache();
            cache.put(TENANT, new LicenseService.CacheEntry(Map.of(), "2026-01-01", null));

            licenseService.registerLicense(TENANT, blob);

            assertThat(cache).doesNotContainKey(TENANT);
        }

        @Test
        @DisplayName("저장 후 버전 조회 DB 오류 → 이전 캐시를 비우고 예외 전파")
        void versionQueryFails_clearsPreviousCache() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseRepository.replaceLicense(TENANT, blob, null, false)).willReturn(1);
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willThrow(new QueryTimeoutException("timeout"));
            ConcurrentMap<Integer, LicenseService.CacheEntry> cache = cache();
            cache.put(TENANT, new LicenseService.CacheEntry(Map.of(), "2026-01-01", null));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(QueryTimeoutException.class);

            assertThat(cache).doesNotContainKey(TENANT);
        }

        @Test
        @DisplayName("저장 후 라이선스 조회 DB 오류 → 이전 캐시를 비우고 예외 전파")
        void licenseQueryFails_clearsPreviousCache() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseRepository.replaceLicense(TENANT, blob, null, false)).willReturn(1);
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(Instant.now(), null));
            given(licenseQueryRepository.findLicense(TENANT)).willThrow(new QueryTimeoutException("timeout"));
            ConcurrentMap<Integer, LicenseService.CacheEntry> cache = cache();
            cache.put(TENANT, new LicenseService.CacheEntry(Map.of(), "2026-01-01", null));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(QueryTimeoutException.class);

            assertThat(cache).doesNotContainKey(TENANT);
        }
    }

    @Nested
    @DisplayName("getLicense() - DB 에 저장된 라이선스 로드")
    class Load {

        @Test
        @DisplayName("Drive 라이선스 → 타입·좌석 수를 읽는다")
        void driveLicense_loaded() throws Exception {
            Instant updatedAt = Instant.now();
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(updatedAt, null));
            given(licenseQueryRepository.findLicense(TENANT)).willReturn(dbRow(blob, updatedAt));
            // 스텁이 없으면 Mockito 가 0L 을 돌려줘 현재 시각이 1970 년이 된다
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());

            Map<String, Object> license = licenseService.getLicense(TENANT);

            assertThat(license.get(LicenseInfos.LICENSE_TYPE)).isEqualTo("SEAT");
            assertThat(license.get(LicenseInfos.CONSTRAIN_VALUE)).isEqualTo("100");
            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(true);
        }

        @Test
        @DisplayName("AES 방식(TF) 행 → 형식 오류")
        void legacyAesRow_rejected() {
            Instant updatedAt = Instant.now();
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(updatedAt, null));
            given(licenseQueryRepository.findLicense(TENANT)).willReturn(dbRow(legacyAesBlob(), updatedAt));

            assertThatThrownBy(() -> licenseService.getLicense(TENANT))
                    .isInstanceOf(LicenseValidatorException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.getCode());
        }

        @Test
        @DisplayName("다른 제품(webOffice) 행 → 형식 오류")
        void otherProductRow_rejected() throws Exception {
            Instant updatedAt = Instant.now();
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(updatedAt, null));
            given(licenseQueryRepository.findLicense(TENANT)).willReturn(dbRow(sign("webOffice", trustedKey), updatedAt));

            assertThatThrownBy(() -> licenseService.getLicense(TENANT))
                    .isInstanceOf(LicenseValidatorException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.getCode());
        }
    }

    @Nested
    @DisplayName("registerLicense() - 온라인 인증 라이선스")
    class RegisterOnline {

        @Test
        @DisplayName("오프라인 라이선스 → 인증하지 않는다")
        void offline_notAuthenticated() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey);

            licenseService.registerLicense(TENANT, blob);

            then(licenseAuthService).should(never()).authenticateForRegistration(anyInt(), any(), any(), any());
            then(licenseRepository).should().replaceLicense(TENANT, blob, null, false);
        }

        @Test
        @DisplayName("인증 서버가 무효화로 거절 → 6985, 저장 안 함")
        void inactive_rejected() throws Exception {
            byte[] blob = signOnline();
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), isNull()))
                    .willReturn(authResult(LicenseAuthStatus.INACTIVE, "6985"));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.LICENSE_INACTIVE);

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("인증 서버가 종료일 경과로 거절 → 6989, 저장 안 함")
        void expired_rejected() throws Exception {
            byte[] blob = signOnline();
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), isNull()))
                    .willReturn(authResult(LicenseAuthStatus.EXPIRED, "6989"));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.LICENSE_DATE_INVALID);

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("연결 실패 → 저장하고 유예 1일차로 기록")
        void networkFailure_savedWithGrace() throws Exception {
            byte[] blob = signOnline();
            LicenseAuthResult grace = authResult(LicenseAuthStatus.GRACE_PERIOD, null);
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), isNull())).willReturn(grace);

            licenseService.registerLicense(TENANT, blob);

            then(licenseRepository).should().replaceLicense(TENANT, blob, grace.toData(TENANT), false);
        }

        @Test
        @DisplayName("무효화된 같은 라이선스를 다시 등록 → 저장된 상태로 판정, 연결 실패여도 6985 로 거부")
        void sameLicenseInactive_rejected() throws Exception {
            byte[] blob = signOnline();
            LicenseAuthStateData saved = savedAuthState(signOnline(), LicenseAuthStatus.INACTIVE, 0);
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(saved);
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), eq(saved)))
                    .willReturn(authResult(LicenseAuthStatus.INACTIVE, null));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.LICENSE_INACTIVE);

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("인증 만료된 같은 라이선스를 다시 등록해 그대로 인증 만료 → 6895 로 거부")
        void sameLicenseAuthExpired_rejected() throws Exception {
            byte[] blob = signOnline();
            LicenseAuthStateData saved = savedAuthState(signOnline(), LicenseAuthStatus.AUTH_EXPIRED, 31);
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(saved);
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), eq(saved)))
                    .willReturn(new LicenseAuthResult(LicenseAuthStatus.AUTH_EXPIRED, 32, Instant.now(), null, null, null, null));

            assertThatThrownBy(() -> licenseService.registerLicense(TENANT, blob))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.AUTH_SERVER_FAILURE);

            then(licenseRepository).should(never()).replaceLicense(anyInt(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("같은 라이선스를 다시 등록해 인증 성공 → 인증 이력을 지우지 않고 기록")
        void sameLicenseSuccess_keepsAuth() throws Exception {
            byte[] blob = signOnline();
            LicenseAuthStateData saved = savedAuthState(signOnline(), LicenseAuthStatus.INACTIVE, 0);
            LicenseAuthResult success = authResult(LicenseAuthStatus.SUCCESS, null);
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(saved);
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), eq(saved))).willReturn(success);

            licenseService.registerLicense(TENANT, blob);

            then(licenseRepository).should().replaceLicense(TENANT, blob, success.toData(TENANT), true);
        }

        @Test
        @DisplayName("다른 라이선스로 바꿔 등록 → 이전 상태를 이어받지 않는다")
        void otherLicense_startsFresh() throws Exception {
            byte[] blob = signOnline();
            LicenseAuthResult grace = authResult(LicenseAuthStatus.GRACE_PERIOD, null);
            given(licenseQueryRepository.findAuthState(TENANT))
                    .willReturn(savedAuthState(signOnline(200L), LicenseAuthStatus.INACTIVE, 0));
            given(licenseAuthService.authenticateForRegistration(eq(TENANT), eq(blob), any(), isNull())).willReturn(grace);

            licenseService.registerLicense(TENANT, blob);

            then(licenseRepository).should().replaceLicense(TENANT, blob, grace.toData(TENANT), false);
        }

        @Test
        @DisplayName("인증 주소가 없는 온라인 라이선스 → 6997, 인증하지 않음")
        void noAuthUrl_rejected() throws Exception {
            byte[] blob = sign(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, trustedKey, "seat::100,auth");

            assertRejected(() -> licenseService.registerLicense(TENANT, blob), "online license without auth url");

            then(licenseAuthService).should(never()).authenticateForRegistration(anyInt(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getLicense() - 온라인 인증 결과 반영")
    class LoadOnline {

        private Map<String, Object> load(LicenseAuthStateData authState) throws Exception {
            Instant updatedAt = Instant.now();
            given(licenseQueryRepository.findLicenseVersion(TENANT)).willReturn(new LicenseVersion(updatedAt, Instant.now()));
            given(licenseQueryRepository.findLicense(TENANT)).willReturn(dbRow(signOnline(), updatedAt));
            given(licenseQueryRepository.getServerTimeMS()).willReturn(System.currentTimeMillis());
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(authState);
            return licenseService.getLicense(TENANT);
        }

        @Test
        @DisplayName("서명된 원장 값이 파일 값보다 우선한다 (종료일·좌석 수)")
        void signedLedgerOverridesFile() throws Exception {
            String endDate = LocalDateTime.now().plusYears(2).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String ledger = signedLedger("licenseId:100|startDate:20260101|endDate:" + endDate
                    + "|addFlag:seat::300,auth::" + AUTH_URL + "|licenseType:SEAT");

            Map<String, Object> license = load(authState(LicenseAuthStatus.SUCCESS, ledger));

            assertThat(license.get(Constants.LICENSE.ISSUED_ON)).isEqualTo("2026-01-01");
            assertThat(license.get(Constants.LICENSE.EXPIRES_ON))
                    .isEqualTo(endDate.substring(0, 4) + "-" + endDate.substring(4, 6) + "-" + endDate.substring(6));
            assertThat(license.get(LicenseInfos.CONSTRAIN_VALUE)).isEqualTo("300");
            assertThat(license.get(Constants.LICENSE.ONLINE_AUTH)).isEqualTo(true);
            assertThat(license.get(Constants.LICENSE.AUTH_STATUS)).isEqualTo("SUCCESS");
            assertThat(license.get(Constants.LICENSE.LAST_AUTH_AT)).isEqualTo("2026-10-13 03:10:00");
            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(true);
        }

        @Test
        @DisplayName("다른 라이선스의 원장 값은 쓰지 않는다")
        void ledgerForOtherLicense_ignored() throws Exception {
            String ledger = signedLedger("licenseId:999|startDate:20200101|endDate:20200102|addFlag:seat::1|licenseType:SEAT");

            Map<String, Object> license = load(authState(LicenseAuthStatus.SUCCESS, ledger));

            assertThat(license.get(LicenseInfos.CONSTRAIN_VALUE)).isEqualTo("100");
            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(true);
        }

        @Test
        @DisplayName("무효화 → 만료일과 상관없이 state=false, 상태 코드 6985")
        void inactive_blocked() throws Exception {
            Map<String, Object> license = load(authState(LicenseAuthStatus.INACTIVE, null));

            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(false);
            assertThat(licenseService.getLicenseStateMap(TENANT).get("code")).isEqualTo(ResultCode.LICENSE_INACTIVE.getCode());
        }

        @Test
        @DisplayName("인증 서버가 만료로 판정 → 만료일 기준 유예 없이 state=false, 상태 코드 6989")
        void expired_blocked() throws Exception {
            Map<String, Object> license = load(authState(LicenseAuthStatus.EXPIRED, null));

            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(false);
            assertThat(licenseService.getLicenseStateMap(TENANT).get("code")).isEqualTo(ResultCode.LICENSE_DATE_INVALID.getCode());
        }

        @Test
        @DisplayName("인증 실패 30일 초과 → state=false, 상태 코드 6895")
        void authExpired_blocked() throws Exception {
            Map<String, Object> license = load(authState(LicenseAuthStatus.AUTH_EXPIRED, null));

            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(false);
            assertThat(licenseService.getLicenseStateMap(TENANT).get("code")).isEqualTo(ResultCode.AUTH_SERVER_FAILURE.getCode());
        }

        @Test
        @DisplayName("유예 중 → 막지 않는다")
        void grace_notBlocked() throws Exception {
            Map<String, Object> license = load(authState(LicenseAuthStatus.GRACE_PERIOD, null));

            assertThat(license.get(Constants.LICENSE.STATE)).isEqualTo(true);
            assertThat(licenseService.getLicenseStateMap(TENANT).get("code")).isNull();
        }
    }
}
