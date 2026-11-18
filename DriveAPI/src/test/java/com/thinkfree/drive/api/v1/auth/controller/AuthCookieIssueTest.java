package com.thinkfree.drive.api.v1.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.api.v1.auth.request.EntryKeyVerifyRequest;
import com.thinkfree.drive.api.v1.auth.request.LoginRequest;
import com.thinkfree.drive.api.v1.auth.response.AnonymousLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.AuthLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoJwtLoginResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSessionResponse;
import com.thinkfree.drive.api.v1.auth.usecase.AuthUseCase;
import com.thinkfree.drive.api.v1.token.controller.TokenReissueController;
import com.thinkfree.drive.api.v1.token.response.TokenResponse;
import com.thinkfree.drive.api.v1.token.usecase.TokenReissueUseCase;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.cookie.AuthCookieProperty;
import com.thinkfree.drive.auth.properties.JwtProperty;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 로그인·재발급 응답의 Set-Cookie — 로그인 API 별 쿠키 이름과 테넌트 Path. 토큰은 응답 body 에 담지 않는다.
 */
@DisplayName("로그인·재발급 Set-Cookie, 로그아웃 쿠키 삭제")
class AuthCookieIssueTest {

    private final AuthCookieManager authCookieManager =
            new AuthCookieManager(new AuthCookieProperty(true, "Lax"),
                    new JwtProperty(new JwtProperty.Secret("test-secret"), Duration.ofHours(1), Duration.ofDays(7)));

    private AuthUseCase authUseCase;
    private AuthController authController;
    private HttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authUseCase = mock(AuthUseCase.class);
        authController = new AuthController(authUseCase, authCookieManager);
        request = new TenantPathRequestWrapper(new MockHttpServletRequest(), "/t/acme");
        response = new MockHttpServletResponse();
    }

    private static LoginResponse loginResponse() {
        return LoginResponse.builder().accessToken("at").refreshToken("rt").build();
    }

    private List<String> setCookies() {
        return response.getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    @DisplayName("로그인 응답 body 에는 access·refresh 토큰이 없다")
    void noTokenInBody() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String login = objectMapper.writeValueAsString(loginResponse());
        String anonymous = objectMapper.writeValueAsString(AnonymousLoginResponse.builder().accessToken("at").refreshToken("rt").build());
        String ssoSession = objectMapper.writeValueAsString(
                new SsoSessionResponse.Login(1, "u", "n", null, null, null, null, null, "at", "rt"));
        String ssoJwt = objectMapper.writeValueAsString(
                new SsoJwtLoginResponse.Login(1, "u", "n", null, null, null, null, null, "at", "rt"));

        assertThat(List.of(login, anonymous, ssoSession, ssoJwt))
                .allSatisfy(json -> assertThat(json).doesNotContain("accessToken", "refreshToken"));
    }

    @Nested
    @DisplayName("로그인 API 별 쿠키 이름")
    class Login {

        @Test
        @DisplayName("사용자 로그인 — DRIVE_AT/RT_USER, 테넌트 Path")
        void userLogin() {
            when(authUseCase.login(any(), any())).thenReturn(loginResponse());

            authController.login(new LoginRequest("user@thinkfree.com", "pw"), request, response);

            assertThat(setCookies()).hasSize(2);
            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_USER=at;").contains("Path=/t/acme/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_USER=rt;").contains("Path=/t/acme/api/v1/token/reissue;");
        }

        @Test
        @DisplayName("사용자 로그인이 MFA 챌린지면 쿠키를 내려주지 않는다")
        void userLoginMfaChallenge() {
            AuthLoginResponse challenge = mock(AuthLoginResponse.class);
            when(challenge.httpStatus()).thenReturn(HttpStatus.PRECONDITION_REQUIRED);
            when(authUseCase.login(any(), any())).thenReturn(challenge);

            authController.login(new LoginRequest("user@thinkfree.com", "pw"), request, response);

            assertThat(setCookies()).isEmpty();
        }

        @Test
        @DisplayName("관리자 로그인 — DRIVE_AT/RT_ADMIN")
        void adminLogin() {
            when(authUseCase.adminLogin(any(), any())).thenReturn(loginResponse());

            authController.adminLogin(new LoginRequest("admin@thinkfree.com", "pw"), request, response);

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=at;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=rt;");
        }

        @Test
        @DisplayName("슈퍼관리자 로그인 — DRIVE_AT/RT_SUPER, Path 는 테넌트 접두 없음")
        void superAdminLogin() {
            when(authUseCase.superAdminLogin(any(), any())).thenReturn(loginResponse());

            authController.superAdminLogin(new LoginRequest("super@thinkfree.com", "pw"), request, response);

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_SUPER=at;").contains("Path=/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_SUPER=rt;").contains("Path=/api/v1/token/reissue;");
        }

        @Test
        @DisplayName("슈퍼관리자의 테넌트 관리자 진입 — 그 테넌트 경로의 DRIVE_AT/RT_ADMIN")
        void entryKey() {
            when(authUseCase.loginWithEntryKey(anyString(), any())).thenReturn(loginResponse());
            authController.verifyAdminEntryKey(new EntryKeyVerifyRequest("entry-key"), request, response);

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=at;").contains("Path=/t/acme/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=rt;");
        }
    }

    @Nested
    @DisplayName("로그아웃 — X-Drive-Role 로 고른 역할의 쿠키만 삭제")
    class Logout {

        @Test
        @DisplayName("쿠키의 access 토큰을 넘기고 그 역할의 access·refresh 쿠키를 Max-Age=0 으로 지운다")
        void expiresCookiesOfRole() {
            MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/t/acme/api/v1/auth/logout");
            raw.addHeader(AuthCookieManager.ROLE_HEADER, "ADMIN");
            raw.setCookies(new Cookie("DRIVE_AT_ADMIN", "admin-at"), new Cookie("DRIVE_AT_USER", "user-at"));
            HttpServletRequest logoutRequest = new TenantPathRequestWrapper(raw, "/t/acme");

            authController.logout(logoutRequest, response);

            verify(authUseCase).logout("admin-at", logoutRequest);
            assertThat(setCookies()).hasSize(2);
            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=;").contains("Path=/t/acme/;", "Max-Age=0;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=;").contains("Max-Age=0;");
        }

        @Test
        @DisplayName("access 쿠키가 없어도(만료돼 브라우저가 지움) 쿠키 삭제는 내려준다")
        void noAccessCookie() {
            MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
            raw.addHeader(AuthCookieManager.ROLE_HEADER, "USER");

            authController.logout(raw, response);

            verify(authUseCase).logout(null, raw);
            assertThat(setCookies()).hasSize(2);
        }

        @Test
        @DisplayName("X-Drive-Role 이 없으면 어느 쿠키인지 알 수 없어 아무것도 하지 않는다")
        void noRoleHeader() {
            authController.logout(new MockHttpServletRequest("POST", "/api/v1/auth/logout"), response);

            verifyNoInteractions(authUseCase);
            assertThat(setCookies()).isEmpty();
        }
    }

    @Nested
    @DisplayName("재발급 — 요청 때 고른 쿠키와 같은 이름")
    class Reissue {

        private TokenReissueUseCase useCase;
        private TokenReissueController reissueController;
        private MockHttpServletRequest reissueRequest;

        @BeforeEach
        void setUp() {
            useCase = mock(TokenReissueUseCase.class);
            when(useCase.reissueTokens(anyInt(), any(), any())).thenReturn(TokenResponse.of("new-at", "new-rt"));
            reissueController = new TokenReissueController(useCase, authCookieManager);
            reissueRequest = new MockHttpServletRequest();
        }

        private CustomUserDetails user(RoleType role) {
            return new CustomUserDetails(new UserDetailDto(1, "u@thinkfree.com", "u", role.getAuthority(), AuthSource.INTERNAL, 1));
        }

        @Test
        @DisplayName("X-Drive-Role 헤더 역할의 refresh 쿠키로 재발급하고 같은 역할 쿠키로 발급")
        void byHeader() {
            reissueRequest.addHeader(AuthCookieManager.ROLE_HEADER, "ADMIN");
            reissueRequest.setCookies(new Cookie("DRIVE_RT_USER", "user-rt"), new Cookie("DRIVE_RT_ADMIN", "admin-rt"));

            reissueController.reissueTokens(user(RoleType.SUPER_ADMIN), reissueRequest, response);

            verify(useCase).reissueTokens(1, "admin-rt", reissueRequest);
            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=new-at;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=new-rt;");
        }

        @Test
        @DisplayName("헤더나 그 역할의 refresh 쿠키가 없으면 AUTH_INVALID_TOKEN, 쿠키 발급 없음")
        void missingHeaderOrCookie() {
            reissueRequest.setCookies(new Cookie("DRIVE_RT_USER", "user-rt"));
            assertThatThrownBy(() -> reissueController.reissueTokens(user(RoleType.USER), reissueRequest, response))
                    .isInstanceOf(ApiRuntimeException.class);

            reissueRequest.addHeader(AuthCookieManager.ROLE_HEADER, "ADMIN");
            assertThatThrownBy(() -> reissueController.reissueTokens(user(RoleType.USER), reissueRequest, response))
                    .isInstanceOf(ApiRuntimeException.class);

            verify(useCase, never()).reissueTokens(anyInt(), any(), any());
            assertThat(setCookies()).isEmpty();
        }
    }
}
