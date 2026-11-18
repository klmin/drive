package com.thinkfree.drive.license.auth.service;

import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.hancom.weboffice.common.license.model.LicenseWrapper;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.auth.client.LicenseAuthClient;
import com.thinkfree.drive.license.auth.client.LicenseAuthClientProvider;
import com.thinkfree.drive.license.auth.enums.LicenseAuthStatus;
import com.thinkfree.drive.license.auth.enums.LicenseAuthType;
import com.thinkfree.drive.license.auth.request.LicenseAuthRequest;
import com.thinkfree.drive.license.auth.response.LicenseAuthResponse;
import com.thinkfree.drive.license.auth.result.LicenseAuthResult;
import com.thinkfree.drive.license.auth.result.SignedLicenseInfo;
import com.thinkfree.drive.license.auth.support.AuthResponseVerifier;
import com.thinkfree.drive.license.auth.support.DeviceInfoProvider;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.repository.LicenseRepository;
import com.thinkfree.drive.license.repository.SeatLicenseRepository;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("LicenseAuthService 판정")
class LicenseAuthServiceTest {

    private static final int TENANT = 1;
    private static final String BASE_URL = "https://auth.example/api/public/v1";
    private static final String JWS = "header.payload.signature";
    private static final byte[] BLOB = ("TR" + JWS).getBytes(StandardCharsets.US_ASCII);
    /** 03:10 (drive.time-zone 기준) — "같은 날" 판정이 설정 시간대를 따르는지 보려고 자정 가까이 둔다 */
    private static final Instant NOW = DriveTime.toInstant(LocalDateTime.of(2026, 10, 13, 3, 10));
    /** 인증을 시작할 때 읽은 파일 저장 시각 */
    private static final Instant UPDATED_AT = NOW.minus(10, ChronoUnit.DAYS);

    @Mock LicenseRepository licenseRepository;
    @Mock LicenseQueryRepository licenseQueryRepository;
    @Mock SeatLicenseRepository seatLicenseRepository;
    @Mock LicenseAuthClientProvider clientProvider;
    @Mock AuthResponseVerifier responseVerifier;
    @Mock DeviceInfoProvider deviceInfoProvider;
    @Mock LicenseAuthClient client;

    @InjectMocks LicenseAuthService licenseAuthService;

    @BeforeEach
    void setUp() {
        lenient().when(licenseQueryRepository.getServerTimeMS()).thenReturn(NOW.toEpochMilli());
        lenient().when(clientProvider.get(BASE_URL)).thenReturn(client);
        lenient().when(deviceInfoProvider.deviceId()).thenReturn("device-1");
        lenient().when(seatLicenseRepository.countActiveUsers(TENANT)).thenReturn(42);
    }

    private static LicenseWrapper wrapper(String addFlag) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put(LicenseInfos.LICENSE_ID, 100L);
        map.put(LicenseInfos.CATEGORY, "PRODUCTION");
        map.put(LicenseInfos.LICENSE_TYPE, Constants.LICENSE.LICENSE_TYPE_SEAT);
        map.put(LicenseInfos.PRODUCT_NAME, Constants.LICENSE.PRODUCT_VALIDATOR_KEY);
        map.put(LicenseInfos.CLIENT_ID, "7");
        map.put(LicenseInfos.CLIENT_NAME, "Thinkfree");
        map.put(LicenseInfos.DATE_ISSUED, "20261001");
        map.put(LicenseInfos.DATE_EXPIRED, "20271001");
        map.put(LicenseInfos.ADD_FLAG, addFlag);
        return new LicenseWrapper(Constants.LICENSE.PRODUCT_VALIDATOR_KEY, map);
    }

    private static LicenseWrapper onlineWrapper() throws Exception {
        return wrapper("seat::100,auth::" + BASE_URL + ",stats");
    }

    private static LicenseAuthStateData previous(LicenseAuthStatus status, int failCount, Instant checkedAt) {
        return new LicenseAuthStateData(BLOB, status.name(), failCount, checkedAt, NOW.minus(30, ChronoUnit.DAYS), null, null, UPDATED_AT);
    }

    private static LicenseAuthResponse successResponse(String signedData) {
        return new LicenseAuthResponse("SUCCESS", "0", "success", "1759280000000", 1760321400000L, signedData, "k1");
    }

    private static LicenseAuthResponse failResponse(String errorCode) {
        return new LicenseAuthResponse("FAIL", errorCode, "fail", null, null, null, null);
    }

    private LicenseAuthResult request(LicenseAuthStateData previous) throws Exception {
        return licenseAuthService.request(TENANT, BLOB, onlineWrapper(), LicenseAuthType.AUTO, previous);
    }

    @Nested
    @DisplayName("온라인 여부·주소")
    class OnlineAndUrl {

        @Test
        @DisplayName("auth 토큰이 없으면 오프라인")
        void noAuthToken_offline() throws Exception {
            assertThat(LicenseAuthService.isOnline(wrapper("seat::100"))).isFalse();
        }

        @Test
        @DisplayName("주소 없이 auth 만 있으면 온라인이지만 주소는 null")
        void authWithoutUrl_nullBaseUrl() throws Exception {
            LicenseWrapper wrapper = wrapper("seat::100,auth");

            assertThat(LicenseAuthService.isOnline(wrapper)).isTrue();
            assertThat(LicenseAuthService.authBaseUrl(wrapper)).isNull();
        }

        @Test
        @DisplayName("주소 끝의 / 는 뗀다")
        void trailingSlash_trimmed() throws Exception {
            assertThat(LicenseAuthService.authBaseUrl(wrapper("auth::" + BASE_URL + "/"))).isEqualTo(BASE_URL);
        }
    }

    @Nested
    @DisplayName("요청")
    class Request {

        @Test
        @DisplayName("SEAT 는 활성 사용자 수를 cur 로, 파일은 TR 을 뗀 문자열로 보낸다")
        void requestBody() throws Exception {
            given(client.auth(eq("auto"), any())).willReturn(failResponse("6988"));

            request(null);

            ArgumentCaptor<LicenseAuthRequest> captor = ArgumentCaptor.forClass(LicenseAuthRequest.class);
            then(client).should().auth(eq("auto"), captor.capture());
            LicenseAuthRequest body = captor.getValue();
            assertThat(body.licenseId()).isEqualTo(100L);
            assertThat(body.clientId()).isEqualTo(7L);
            assertThat(body.cur()).isEqualTo(42L);
            assertThat(body.licenseFile()).isEqualTo(JWS);
            assertThat(body.deviceId()).isEqualTo("device-1");
            assertThat(body.requestedAt()).isEqualTo(String.valueOf(NOW.toEpochMilli()));
        }

        @Test
        @DisplayName("수동 인증을 오프라인 라이선스로 부르면 6899")
        void manual_offline_throws() {
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(new LicenseAuthStateData(BLOB, null, 0, null, null, null, null, UPDATED_AT));

            assertThatThrownBy(() -> licenseAuthService.authenticateManually(TENANT))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting("code").isEqualTo(ResultCode.AUTH);
        }

        @Test
        @DisplayName("일일 인증: 한 테넌트에서 오류가 나도 나머지 테넌트는 계속한다")
        void autoAll_continuesAfterError() {
            given(licenseQueryRepository.findOnlineLicensedTenantSeqs()).willReturn(List.of(1, 2));
            given(licenseQueryRepository.findAuthState(1)).willThrow(new RuntimeException("db"));
            given(licenseQueryRepository.findAuthState(2)).willReturn(new LicenseAuthStateData(BLOB, null, 0, null, null, null, null, UPDATED_AT));

            licenseAuthService.autoAll();

            then(licenseQueryRepository).should().findAuthState(2);
        }

        @Test
        @DisplayName("오프라인 라이선스는 인증 서버를 부르지 않는다")
        void offline_notCalled() {
            given(licenseQueryRepository.findAuthState(TENANT)).willReturn(new LicenseAuthStateData(BLOB, null, 0, null, null, null, null, UPDATED_AT));

            assertThat(licenseAuthService.authenticate(TENANT, LicenseAuthType.AUTO)).isNull();

            then(clientProvider).should(never()).get(anyString());
            then(licenseRepository).should(never()).updateAuthResultIfUnchanged(any(), any());
        }

        @Test
        @DisplayName("결과는 인증을 시작할 때 읽은 파일 저장 시각을 조건으로 기록한다")
        void save_conditionalOnUpdatedAt() {
            LicenseAuthResult authResult = new LicenseAuthResult(LicenseAuthStatus.SUCCESS, 0, NOW, NOW, null, null, null);
            given(licenseRepository.updateAuthResultIfUnchanged(authResult.toData(TENANT), UPDATED_AT)).willReturn(1);

            licenseAuthService.save(TENANT, authResult, UPDATED_AT);

            then(licenseRepository).should().updateAuthResultIfUnchanged(authResult.toData(TENANT), UPDATED_AT);
        }

        @Test
        @DisplayName("인증하는 동안 라이선스가 바뀌었으면 결과를 버리고 6899")
        void save_licenseChanged_discarded() {
            LicenseAuthResult authResult = new LicenseAuthResult(LicenseAuthStatus.INACTIVE, 0, NOW, null, null, null, "6985");
            given(licenseRepository.updateAuthResultIfUnchanged(any(), eq(UPDATED_AT))).willReturn(0);

            assertThatThrownBy(() -> licenseAuthService.save(TENANT, authResult, UPDATED_AT))
                    .isInstanceOfSatisfying(ApiRuntimeException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ResultCode.AUTH);
                        assertThat(e.getContext()).containsEntry("reason", "license changed during auth");
                    });
        }
    }

    @Nested
    @DisplayName("판정")
    class Judge {

        @Test
        @DisplayName("서명이 확인된 성공 → SUCCESS, 실패 횟수 0, 서명 값 보관")
        void success() throws Exception {
            given(client.auth(anyString(), any())).willReturn(successResponse("signed"));
            given(responseVerifier.verify(BASE_URL, "signed", "k1"))
                    .willReturn(new SignedLicenseInfo(100L, "20261001", "20271001", "seat::100", "SEAT"));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD, 3, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.SUCCESS);
            assertThat(authResult.failCount()).isZero();
            assertThat(authResult.signedData()).isEqualTo("signed");
            assertThat(authResult.successAt()).isNotNull();
            assertThat(authResult.initAt()).isNotNull();
        }

        @Test
        @DisplayName("서명이 맞지 않는 성공 → 실패로 친다")
        void successWithBadSignature_isFailure() throws Exception {
            given(client.auth(anyString(), any())).willReturn(successResponse("forged"));
            given(responseVerifier.verify(BASE_URL, "forged", "k1")).willThrow(new IllegalStateException("bad"));

            LicenseAuthResult authResult = request(null);

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.GRACE_PERIOD);
            assertThat(authResult.failCount()).isEqualTo(1);
            assertThat(authResult.signedData()).isNull();
        }

        @Test
        @DisplayName("다른 라이선스에 대한 서명 → 실패로 친다")
        void signedForOtherLicense_isFailure() throws Exception {
            given(client.auth(anyString(), any())).willReturn(successResponse("signed"));
            given(responseVerifier.verify(BASE_URL, "signed", "k1"))
                    .willReturn(new SignedLicenseInfo(999L, "20261001", "20271001", "", "SEAT"));

            assertThat(request(null).status()).isEqualTo(LicenseAuthStatus.GRACE_PERIOD);
        }

        @Test
        @DisplayName("무효화(6985) → INACTIVE, 실패 횟수는 그대로")
        void inactive() throws Exception {
            given(client.auth(anyString(), any())).willReturn(failResponse("6985"));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD, 2, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.INACTIVE);
            assertThat(authResult.failCount()).isEqualTo(2);
            assertThat(authResult.status().isBlocked()).isTrue();
        }

        @Test
        @DisplayName("종료일 경과(6989) → EXPIRED, 유예 없이 바로 막는다")
        void dateInvalid() throws Exception {
            given(client.auth(anyString(), any())).willReturn(failResponse("6989"));

            LicenseAuthResult authResult = request(null);

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.EXPIRED);
            assertThat(authResult.status().isBlocked()).isTrue();
        }

        @Test
        @DisplayName("파일 만료(6995) → 6989 와 같은 EXPIRED, 실패 횟수는 그대로")
        void fileExpired_sameAsDateInvalid() throws Exception {
            given(client.auth(anyString(), any())).willReturn(failResponse("6995"));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD, 2, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.EXPIRED);
            assertThat(authResult.failCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("시작 전(6987) → NOT_STARTED")
        void notStarted() throws Exception {
            given(client.auth(anyString(), any())).willReturn(failResponse("6987"));

            assertThat(request(null).status()).isEqualTo(LicenseAuthStatus.NOT_STARTED);
        }

        @Test
        @DisplayName("연결 실패, 전날 실패 기록 → 실패 횟수 +1")
        void networkFailure_nextDay_increments() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD, 2, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.GRACE_PERIOD);
            assertThat(authResult.failCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("연결 실패, 같은 날 이미 실패 → 실패 횟수 그대로")
        void networkFailure_sameDay_notIncremented() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD, 2, NOW.minus(1, ChronoUnit.HOURS)));

            assertThat(authResult.failCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 날 성공 뒤 첫 실패 → 1")
        void failureAfterSuccessSameDay_countsOne() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.SUCCESS, 0, NOW.minus(1, ChronoUnit.HOURS)));

            assertThat(authResult.failCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("허용 일수를 넘긴 실패 → AUTH_EXPIRED (차단)")
        void failureBeyondLimit_authExpired() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.GRACE_PERIOD,
                    Constants.LICENSE.AUTH_MAX_FAIL_DAYS, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.AUTH_EXPIRED);
            assertThat(authResult.status().isBlocked()).isTrue();
        }

        @Test
        @DisplayName("무효화된 라이선스는 연결 실패로 풀리지 않는다")
        void inactiveStaysOnNetworkFailure() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = request(previous(LicenseAuthStatus.INACTIVE, 0, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.INACTIVE);
        }

        @Test
        @DisplayName("무효화된 같은 라이선스를 다시 등록 → 연결 실패로 풀리지 않는다")
        void registration_inactiveStaysOnNetworkFailure() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = licenseAuthService.authenticateForRegistration(TENANT, BLOB, onlineWrapper(),
                    previous(LicenseAuthStatus.INACTIVE, 0, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.INACTIVE);
        }

        @Test
        @DisplayName("인증 만료된 같은 라이선스를 다시 등록 → 실패 횟수를 이어 세어 인증 만료로 남는다")
        void registration_authExpiredContinuesCount() throws Exception {
            given(client.auth(anyString(), any())).willThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

            LicenseAuthResult authResult = licenseAuthService.authenticateForRegistration(TENANT, BLOB, onlineWrapper(),
                    previous(LicenseAuthStatus.AUTH_EXPIRED, Constants.LICENSE.AUTH_MAX_FAIL_DAYS + 1, NOW.minus(1, ChronoUnit.DAYS)));

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.AUTH_EXPIRED);
            assertThat(authResult.failCount()).isEqualTo(Constants.LICENSE.AUTH_MAX_FAIL_DAYS + 2);
        }

        @Test
        @DisplayName("주소 없는 온라인 라이선스 → 호출 없이 실패로 친다")
        void noBaseUrl_failureWithoutCall() throws Exception {
            LicenseAuthResult authResult = licenseAuthService.request(TENANT, BLOB, wrapper("seat::100,auth"),
                    LicenseAuthType.AUTO, null);

            assertThat(authResult.status()).isEqualTo(LicenseAuthStatus.GRACE_PERIOD);
            then(clientProvider).should(never()).get(anyString());
            then(seatLicenseRepository).should(never()).countActiveUsers(anyInt());
        }
    }
}
