package com.thinkfree.drive.api.v1.auth.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaChallengeResponse;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaSetupChallengeResponse;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaSetupResponse;
import com.thinkfree.drive.auth.result.AnonymousLoginResult;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.auth.result.LoginMfaSetupResult;
import com.thinkfree.drive.auth.result.LoginResult;
import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthService 결과(*Result) → 웹 응답 변환이 기존 응답(서비스가 직접 만들던 LoginResponse.of 등)과 같은 JSON 을 만드는지.
 * 기대값은 기존 정적 팩토리와 같은 순서·같은 값으로 빌더를 직접 채운 응답이다.
 */
@DisplayName("AuthLoginResponse.from — 로그인 결과 → 웹 응답 JSON 동일성")
class AuthLoginResponseMappingTest {

    /** 앱처럼 시간대를 지정(spring.jackson.time-zone) — @DriveDateTimeFormat 패턴이 Instant 를 벽시계로 출력 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                                                                .setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

    private static UserAuthData authUser() {
        Instant t = Instant.parse("2026-10-14T01:02:03Z");
        return new UserAuthData(7, "user7@thinkfree.com", "사용자7", UserStatus.ACTIVE, 1000L, 10L,
                1, t, 2, t, UserLanguage.KO, "KR", "010-0000-0000", DefaultLandingPage.HOME, "profile/key", 3,
                MfaStatus.ENABLED, null, 0, null, MfaMethod.TOTP, "secret");
    }

    /** 기존 LoginResponse.of(UserAuthData, …) 와 같은 채움 */
    private static LoginResponse legacyLoginResponse(UserAuthData u, String at, String rt, Boolean hasChangedPassword, String baseDomain, Boolean isFirstLogin) {
        return LoginResponse.builder()
                .userSeq(u.getUserSeq())
                .userId(u.getUserId())
                .userName(u.getUserName())
                .totalQuotaByte(u.getTotalQuotaByte())
                .useQuotaByte(u.getUseQuotaByte())
                .roles(List.of(RoleType.ADMIN))
                .userLanguage(u.getUserLanguage())
                .defaultLandingPage(u.getDefaultLandingPage())
                .accessToken(at)
                .refreshToken(rt)
                .hasChangedPassword(hasChangedPassword)
                .baseDomain(baseDomain)
                .isFirstLogin(isFirstLogin)
                .build();
    }

    private JsonNode json(Object o) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(o));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("관리자 로그인(isFirstLogin 포함) — 토큰 제외 11개 필드 전부 같은 값, 200")
    void adminLogin_allFieldsIdentical() throws Exception {
        UserAuthData u = authUser();
        AuthLoginResult result = LoginResult.of(u, List.of(RoleType.ADMIN), "access", "refresh", true, "drive.example.com", false);

        AuthLoginResponse response = AuthLoginResponse.from(result);

        assertThat(response).isInstanceOf(LoginResponse.class);
        assertThat(response.httpStatus()).isEqualTo(HttpStatus.OK);
        JsonNode actual = json(response);
        assertThat(actual).isEqualTo(json(legacyLoginResponse(u, "access", "refresh", true, "drive.example.com", false)));
        assertThat(fieldNames(actual)).hasSize(11);
        assertThat(fieldNames(actual)).contains("roles").doesNotContain("roleType", "accessToken", "refreshToken");
        actual.fields().forEachRemaining(e -> assertThat(e.getValue().isNull()).as(e.getKey()).isFalse());
    }

    @Test
    @DisplayName("사용자 로그인 — isFirstLogin 은 null 그대로(기존 5인자 of 와 같음)")
    void userLogin_isFirstLoginNull() throws Exception {
        UserAuthData u = authUser();
        LoginResult result = LoginResult.of(u, List.of(RoleType.ADMIN), "access", "refresh", false, null);

        LoginResponse response = LoginResponse.from(result);

        assertThat(response.getIsFirstLogin()).isNull();
        assertThat(json(response)).isEqualTo(json(legacyLoginResponse(u, "access", "refresh", false, null, null)));
    }

    @Test
    @DisplayName("MFA 챌린지 — 428, 토큰·만료(초·시각)·방식 그대로")
    void mfaChallenge() throws Exception {
        var result = AuthLoginResult.MfaChallenge.of("challenge-token", 300, "totp");

        AuthLoginResponse response = AuthLoginResponse.from(result);

        assertThat(response).isInstanceOf(MfaChallengeResponse.class);
        assertThat(response.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(json(response)).isEqualTo(json(new MfaChallengeResponse(MfaChallengeType.VERIFY, "challenge-token", 300, result.expiresAt(), "totp")));
    }

    @Test
    @DisplayName("MFA 설정 챌린지 — 428, 토큰·만료 그대로")
    void mfaSetupChallenge() throws Exception {
        var result = AuthLoginResult.MfaSetupChallenge.of("setup-token", 600);

        AuthLoginResponse response = AuthLoginResponse.from(result);

        assertThat(response).isInstanceOf(MfaSetupChallengeResponse.class);
        assertThat(response.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(json(response)).isEqualTo(json(new MfaSetupChallengeResponse(MfaChallengeType.SETUP, "setup-token", 600, result.expiresAt())));
    }

    @Test
    @DisplayName("로그인 중 MFA 설정 — otpauth·확인 토큰·만료 그대로")
    void mfaSetupForLogin() throws Exception {
        var result = LoginMfaSetupResult.of("otpauth://totp/x", "confirm-token", 180);

        MfaSetupResponse response = MfaSetupResponse.from(result);

        assertThat(json(response)).isEqualTo(json(new MfaSetupResponse("otpauth://totp/x", "confirm-token", 180, result.expiresAt())));
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("익명 로그인 — 토큰 제외 6개 필드 그대로(기존 AnonymousLoginResponse.of 와 같음)")
    void anonymousLogin() throws Exception {
        UserAuthData a = authUser();
        UserDetailData u = new UserDetailData(a.getUserSeq(), a.getUserId(), a.getUserName(), UserStatus.ACTIVE, 0L, 0L,
                null, null, null, null, UserLanguage.EN, null, null, null, null, 3, MfaStatus.DISABLED);

        AnonymousLoginResponse response = AnonymousLoginResponse.from(AnonymousLoginResult.of(u, "access", "refresh", "drive.example.com"));

        AnonymousLoginResponse legacy = AnonymousLoginResponse.builder()
                .userSeq(u.getUserSeq())
                .userId(u.getUserId())
                .userName(u.getUserName())
                .roles(List.of(RoleType.ANONYMOUS))
                .userLanguage(u.getUserLanguage())
                .accessToken("access")
                .refreshToken("refresh")
                .baseDomain("drive.example.com")
                .build();
        JsonNode actual = json(response);
        assertThat(actual).isEqualTo(json(legacy));
        assertThat(fieldNames(actual)).hasSize(6);
        assertThat(fieldNames(actual)).contains("roles").doesNotContain("roleType", "accessToken", "refreshToken");
        actual.fields().forEachRemaining(e -> assertThat(e.getValue().isNull()).as(e.getKey()).isFalse());
    }
}
