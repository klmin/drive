package com.thinkfree.drive.global.ratelimit;

import com.thinkfree.drive.api.v1.auth.controller.AuthController;
import com.thinkfree.drive.api.v1.auth.usecase.AuthUseCase;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.global.exception.ApiGlobalExceptionHandler;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.enums.RoleType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("로그인 요청 수 제한 — 메모리 버킷")
class LoginRateLimitTest {

    final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private LoginRateLimitGuard guard(long perAccount, long perIp) {
        return new LoginRateLimitGuard(new LocalRateLimiter(), new RateLimitProperty(true, perAccount, perIp, 3), meters);
    }

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    @DisplayName("메모리 버킷 — 용량만큼 통과, 다음은 기다릴 시간(양수), 키·규칙마다 따로")
    void localBucket() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        RateLimitRule rule = new RateLimitRule("t", 3, Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("a", rule)).isZero();
        }
        Duration wait = limiter.tryConsume("a", rule);
        assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20));
        assertThat(limiter.tryConsume("b", rule)).as("다른 키").isZero();
        assertThat(limiter.tryConsume("a", new RateLimitRule("u", 3, Duration.ofMinutes(1)))).as("다른 규칙").isZero();
    }

    @Test
    @DisplayName("메모리 버킷은 규칙 기간보다 먼저 버리지 않는다 — 버리면 꽉 찬 새 버킷이 생겨 한도가 늘어남")
    void localBucketIdleExpiryCoversRulePeriod() {
        assertThat(LocalRateLimiter.idleExpiry(new RateLimitRule("short", 1, Duration.ofMinutes(1)))).isEqualTo(LocalRateLimiter.IDLE_EXPIRY);
        assertThat(LocalRateLimiter.idleExpiry(new RateLimitRule("long", 1, Duration.ofHours(1)))).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("같은 아이디는 IP 가 달라도 분당 N 번 — 대소문자·공백은 같은 아이디")
    void perAccountAcrossIps() {
        TenantContext.set(1);
        LoginRateLimitGuard guard = guard(3, 100);
        guard.checkLogin(RoleType.ADMIN, "admin", from("10.0.0.1"));
        guard.checkLogin(RoleType.ADMIN, "Admin ", from("10.0.0.2"));
        guard.checkLogin(RoleType.ADMIN, "ADMIN", from("10.0.0.3"));
        assertThatThrownBy(() -> guard.checkLogin(RoleType.ADMIN, "admin", from("10.0.0.4")))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> {
                    RateLimitExceededException ex = (RateLimitExceededException) e;
                    assertThat(ex.getRule()).isEqualTo("login-account");
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 20L);
                });
        assertThat(meters.counter("drive.rate.limit.rejected", "rule", "login-account").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("테넌트·화면마다 따로 — 한 테넌트 공격이 다른 테넌트, 사용자 화면 공격이 관리자 화면을 막지 않는다")
    void separatedByTenantAndScreen() {
        LoginRateLimitGuard guard = guard(1, 100);
        TenantContext.set(1);
        guard.checkLogin(RoleType.USER, "kim", from("10.0.0.1"));
        assertThatThrownBy(() -> guard.checkLogin(RoleType.USER, "kim", from("10.0.0.1"))).isInstanceOf(RateLimitExceededException.class);
        assertThatNoException().isThrownBy(() -> guard.checkLogin(RoleType.ADMIN, "kim", from("10.0.0.1")));

        TenantContext.set(2);
        assertThatNoException().isThrownBy(() -> guard.checkLogin(RoleType.USER, "kim", from("10.0.0.1")));

        TenantContext.clear();
        assertThatNoException().isThrownBy(() -> guard.checkLogin(RoleType.SUPER_ADMIN, "kim", from("10.0.0.1")));
    }

    @Test
    @DisplayName("같은 IP 는 아이디가 달라도 분당 N 번, 익명 로그인은 IP 만")
    void perIp() {
        TenantContext.set(1);
        LoginRateLimitGuard guard = guard(100, 2);
        guard.checkLogin(RoleType.USER, "a", from("10.0.0.9"));
        guard.checkLogin(RoleType.USER, "b", from("10.0.0.9"));
        assertThatThrownBy(() -> guard.checkLogin(RoleType.USER, "c", from("10.0.0.9")))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("rule").isEqualTo("login-ip");

        for (int i = 0; i < 3; i++) {
            guard.checkAnonymousLogin(from("10.0.0.7"));
        }
        assertThatThrownBy(() -> guard.checkAnonymousLogin(from("10.0.0.7"))).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("꺼져 있으면 제한하지 않는다")
    void disabled() {
        TenantContext.set(1);
        LoginRateLimitGuard guard = new LoginRateLimitGuard(new LocalRateLimiter(), new RateLimitProperty(false, 1, 1, 1), meters);
        for (int i = 0; i < 5; i++) {
            guard.checkLogin(RoleType.USER, "kim", from("10.0.0.1"));
            guard.checkAnonymousLogin(from("10.0.0.1"));
        }
    }

    @Test
    @DisplayName("아이디는 원문이 아니라 해시로 키에 들어간다")
    void accountKeyIsHashed() {
        assertThat(LoginRateLimitGuard.accountKey(" Admin")).isEqualTo(LoginRateLimitGuard.accountKey("admin"))
                .hasSize(32).doesNotContain("admin");
    }

    @Test
    @DisplayName("초과하면 429 + Retry-After, 로그인 처리(UseCase)는 부르지 않는다")
    void http429WithRetryAfter() throws Exception {
        TenantContext.set(1);
        AuthUseCase useCase = mock(AuthUseCase.class);
        LoginRateLimitGuard guard = guard(1, 100);
        guard.checkLogin(RoleType.ADMIN, "admin", from("127.0.0.1")); // 하나 써 둔다
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(useCase, mock(AuthCookieManager.class), guard))
                .setControllerAdvice(new ApiGlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"admin\",\"encPwd\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("REQUEST_013"));
        verify(useCase, never()).adminLogin(any(), any());
    }
}
