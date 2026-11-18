package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessMethodResolver 단위 테스트")
class AccessMethodResolverTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void loginAs(AuthSource authSource) {
        CustomUserDetails details = new CustomUserDetails(
                new UserDetailDto(1, "user@thinkfree.com", "사용자", "ROLE_USER", authSource, 1));
        SecurityContextHolder.getContext()
                             .setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @Test
    @DisplayName("내부 경로 + 쿠키 인증 → WEB")
    void internalPath_cookie_isWeb() {
        request("/api/v1/resources");
        loginAs(AuthSource.INTERNAL);

        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.WEB);
    }

    @Test
    @DisplayName("내부 경로 + 인증 없음 → WEB")
    void internalPath_noAuthentication_isWeb() {
        request("/api/v1/auth/login");

        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.WEB);
    }

    @Test
    @DisplayName("내부 경로 + API Key 인증 → API")
    void internalPath_apiKey_isApi() {
        request("/api/v1/resources");
        loginAs(AuthSource.EXTERNAL);

        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.API);
    }

    @Test
    @DisplayName("외부 경로 → API")
    void externalPath_isApi() {
        request("/api/public/v1/resources");
        loginAs(AuthSource.EXTERNAL);

        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.API);
    }

    @Test
    @DisplayName("MCP 경로 + API Key 인증 → MCP (경로 판정 우선)")
    void mcpPath_apiKey_isMcp() {
        request("/mcp");
        loginAs(AuthSource.EXTERNAL);

        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.MCP);
    }

    @Test
    @DisplayName("요청 없음 → SYSTEM")
    void noRequest_isSystem() {
        assertThat(AccessMethodResolver.resolve(null)).isEqualTo(AccessMethod.SYSTEM);
    }

    @Test
    @DisplayName("override 가 있으면 그대로")
    void override_isKept() {
        request("/api/v1/resources");
        loginAs(AuthSource.EXTERNAL);

        assertThat(AccessMethodResolver.resolve(AccessMethod.SYSTEM)).isEqualTo(AccessMethod.SYSTEM);
    }
}
