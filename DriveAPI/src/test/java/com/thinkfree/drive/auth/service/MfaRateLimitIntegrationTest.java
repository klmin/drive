package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.ratelimit.RateLimitExceededException;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.global.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MFA 코드 확인 요청 수 제한 — 실제 AuthService·JwtProvider·DB. 서명 검증한 챌린지 토큰의 사용자 기준으로,
 * 한도를 넘으면 코드를 비교하기 전에 429(RateLimitExceededException).
 */
@TestPropertySource(properties = {"drive.rate-limit.enabled=true", "drive.rate-limit.mfa-per-user=2"})
@DisplayName("MFA 코드 확인 요청 수 제한 — 사용자별")
class MfaRateLimitIntegrationTest extends ServiceTest {

    private static final int TENANT = 1;

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtProvider jwtProvider;

    @BeforeEach
    void tenant() {
        TenantContext.set(TENANT); // 요청에선 TenantResolverFilter 가 채운다
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private User insert(String userId) {
        return insertUserWithRole(User.createUser(userId, TENANT, Password.encrypt("Mfa!rate1", ENCODER), SignupType.LOCAL, userId,
                UserStatus.ACTIVE, 1024L, 1, null, null, null), RoleType.USER);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("121.157.114.75");
        return r;
    }

    @Test
    @DisplayName("같은 사용자 MFA 확인은 분당 2번까지 코드를 비교하고, 그다음은 비교 없이 429 — 챌린지 토큰을 새로 받아도 같은 한도")
    void mfaVerifyLimitedPerUser() {
        User user = insert("mfr1@test.com");

        for (int i = 0; i < 2; i++) {
            String token = jwtProvider.createMfaChallengeToken(user.getUserSeq(), Duration.ofMinutes(5));
            assertThatThrownBy(() -> authService.verifyMfaLogin(token, "000000", request()))
                    .as("한도 안 — 코드 비교까지 가서 MFA 오류")
                    .isInstanceOf(ApiRuntimeException.class);
        }

        String fresh = jwtProvider.createMfaChallengeToken(user.getUserSeq(), Duration.ofMinutes(5));
        assertThatThrownBy(() -> authService.verifyMfaLogin(fresh, "000000", request()))
                .isInstanceOf(RateLimitExceededException.class);

        User other = insert("mfr2@test.com");
        String otherToken = jwtProvider.createMfaChallengeToken(other.getUserSeq(), Duration.ofMinutes(5));
        assertThatThrownBy(() -> authService.verifyMfaLogin(otherToken, "000000", request()))
                .as("다른 사용자는 따로")
                .isInstanceOf(ApiRuntimeException.class);
    }

    @Test
    @DisplayName("서명이 틀린 토큰은 한도를 쓰지 않고 토큰 오류 — 남의 사용자 번호로 한도를 소진시킬 수 없다")
    void invalidTokenDoesNotConsume() {
        User user = insert("mfr3@test.com");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyMfaLogin("not-a-jwt", "000000", request()))
                    .isInstanceOf(ApiRuntimeException.class);
        }
        String token = jwtProvider.createMfaChallengeToken(user.getUserSeq(), Duration.ofMinutes(5));
        assertThatThrownBy(() -> authService.verifyMfaLogin(token, "000000", request()))
                .isInstanceOf(ApiRuntimeException.class); // 아직 한도 안
    }
}
