package com.thinkfree.drive.auth.cookie;

import com.thinkfree.drive.auth.properties.JwtProperty;
import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthCookieManager — 로그인 토큰 쿠키 발급·삭제·조회")
class AuthCookieManagerTest {

    private static final String TENANT_PREFIX = "/t/acme";
    private static final JwtProperty JWT_PROPERTY =
            new JwtProperty(new JwtProperty.Secret("test-secret"), Duration.ofHours(1), Duration.ofDays(7));

    private AuthCookieManager manager;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        manager = new AuthCookieManager(new AuthCookieProperty(true, "Lax"), JWT_PROPERTY);
        response = new MockHttpServletResponse();
    }

    /** PATH 모드 — TenantFilter 가 넘기는 것처럼 contextPath 에 테넌트 접두가 붙은 요청 */
    private static HttpServletRequest pathModeRequest() {
        return new TenantPathRequestWrapper(new MockHttpServletRequest(), TENANT_PREFIX);
    }

    /** SUBDOMAIN 모드·접두 없는 요청 */
    private static HttpServletRequest plainRequest() {
        return new MockHttpServletRequest();
    }

    private List<String> setCookies() {
        return response.getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("PATH 모드 사용자 — access 는 테넌트 경로, refresh 는 테넌트 재발급 경로")
        void pathModeUser() {
            manager.issue(pathModeRequest(), response, RoleType.USER, "at", "rt");

            assertThat(setCookies()).hasSize(2);
            assertThat(setCookies().get(0))
                    .startsWith("DRIVE_AT_USER=at;")
                    .contains("Path=/t/acme/;", "Max-Age=3600;", "Secure", "HttpOnly", "SameSite=Lax")
                    .doesNotContain("Domain");
            assertThat(setCookies().get(1))
                    .startsWith("DRIVE_RT_USER=rt;")
                    .contains("Path=/t/acme/api/v1/token/reissue;", "Max-Age=604800;", "Secure", "HttpOnly")
                    .doesNotContain("Domain");
        }

        @Test
        @DisplayName("SUBDOMAIN 모드 — Path 는 / 와 /api/v1/token/reissue")
        void subdomainMode() {
            manager.issue(plainRequest(), response, RoleType.ADMIN, "at", "rt");

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=at;").contains("Path=/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=rt;").contains("Path=/api/v1/token/reissue;");
        }

        @Test
        @DisplayName("슈퍼관리자 — 테넌트 접두로 들어온 요청이어도 Path 는 접두 없음")
        void superAdminIgnoresTenantPrefix() {
            manager.issue(pathModeRequest(), response, RoleType.SUPER_ADMIN, "at", "rt");

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_SUPER=at;").contains("Path=/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_SUPER=rt;").contains("Path=/api/v1/token/reissue;");
        }

        @Test
        @DisplayName("슈퍼관리자의 테넌트 관리자 진입 — ADMIN 쿠키 이름·테넌트 Path")
        void superAdminEntryUsesAdminCookie() {
            manager.issue(pathModeRequest(), response, RoleType.ADMIN, "super-admin-at", "super-admin-rt");

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ADMIN=super-admin-at;").contains("Path=/t/acme/;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ADMIN=super-admin-rt;")
                    .contains("Path=/t/acme/api/v1/token/reissue;");
        }

        @Test
        @DisplayName("익명 — DRIVE_AT_ANON")
        void anonymous() {
            manager.issue(pathModeRequest(), response, RoleType.ANONYMOUS, "at", "rt");

            assertThat(setCookies().get(0)).startsWith("DRIVE_AT_ANON=at;");
            assertThat(setCookies().get(1)).startsWith("DRIVE_RT_ANON=rt;");
        }

        @Test
        @DisplayName("secure=false·SameSite 설정값을 따른다")
        void followsProperty() {
            manager = new AuthCookieManager(new AuthCookieProperty(false, "None"), JWT_PROPERTY);

            manager.issue(plainRequest(), response, RoleType.USER, "at", "rt");

            assertThat(setCookies().get(0)).doesNotContain("Secure").contains("SameSite=None", "HttpOnly");
        }
    }

    @Test
    @DisplayName("삭제 — 발급 때와 같은 이름·Path 로 Max-Age=0")
    void expire() {
        manager.expire(pathModeRequest(), response, RoleType.USER);

        assertThat(setCookies()).hasSize(2);
        assertThat(setCookies().get(0)).startsWith("DRIVE_AT_USER=;").contains("Path=/t/acme/;", "Max-Age=0;");
        assertThat(setCookies().get(1)).startsWith("DRIVE_RT_USER=;")
                .contains("Path=/t/acme/api/v1/token/reissue;", "Max-Age=0;");
    }

    @Nested
    @DisplayName("쿠키 역할 판별(X-Drive-Role)")
    class ResolveCookieRole {

        @ParameterizedTest
        @CsvSource({"USER,USER", "ADMIN,ADMIN", "SUPER_ADMIN,SUPER_ADMIN", "ANONYMOUS,ANONYMOUS", "' ADMIN ',ADMIN"})
        void knownRole(String header, RoleType expected) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(AuthCookieManager.ROLE_HEADER, header);

            assertThat(AuthCookieManager.resolveCookieRole(request)).contains(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "admin", "ROLE_ADMIN", "OWNER"})
        @DisplayName("빈 값·역할 이름이 아니면 empty")
        void unknownRole(String header) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(AuthCookieManager.ROLE_HEADER, header);

            assertThat(AuthCookieManager.resolveCookieRole(request)).isEmpty();
        }

        @Test
        @DisplayName("헤더가 없으면 empty")
        void missingHeader() {
            assertThat(AuthCookieManager.resolveCookieRole(new MockHttpServletRequest())).isEmpty();
        }
    }

    @Nested
    @DisplayName("토큰 조회")
    class ResolveToken {

        @Test
        @DisplayName("여러 역할 쿠키가 실려도 지정한 역할의 쿠키만 읽는다")
        void picksCookieOfRole() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(
                    new Cookie("DRIVE_AT_USER", "user-at"),
                    new Cookie("DRIVE_AT_ADMIN", "admin-at"),
                    new Cookie("DRIVE_AT_SUPER", "super-at"),
                    new Cookie("DRIVE_RT_ADMIN", "admin-rt"));

            assertThat(AuthCookieManager.resolveAccessToken(request, RoleType.USER)).contains("user-at");
            assertThat(AuthCookieManager.resolveAccessToken(request, RoleType.ADMIN)).contains("admin-at");
            assertThat(AuthCookieManager.resolveAccessToken(request, RoleType.SUPER_ADMIN)).contains("super-at");
            assertThat(AuthCookieManager.resolveRefreshToken(request, RoleType.ADMIN)).contains("admin-rt");
        }

        @Test
        @DisplayName("쿠키가 없거나 값이 비면 empty")
        void missingCookie() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            assertThat(AuthCookieManager.resolveAccessToken(request, RoleType.USER)).isEmpty();

            request.setCookies(new Cookie("DRIVE_AT_USER", ""));
            assertThat(AuthCookieManager.resolveAccessToken(request, RoleType.USER)).isEmpty();
            assertThat(AuthCookieManager.resolveRefreshToken(request, RoleType.USER)).isEmpty();
        }
    }
}
