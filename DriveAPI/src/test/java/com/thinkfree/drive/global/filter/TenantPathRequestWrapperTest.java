package com.thinkfree.drive.global.filter;

import com.thinkfree.drive.util.RequestPathUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantPathRequestWrapper — contextPath 에뮬레이션")
class TenantPathRequestWrapperTest {

    private static final String PREFIX = "/t/acme";

    @Test
    @DisplayName("contextPath 에 /t/{sub} 를 붙이고 servletPath 에서는 떼며 requestURI 는 그대로")
    void emulatesContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/t/acme/api/v1/users/me");
        request.setServletPath("/t/acme/api/v1/users/me");

        TenantPathRequestWrapper wrapper = new TenantPathRequestWrapper(request, PREFIX);

        assertThat(wrapper.tenantPrefix()).isEqualTo("/t/acme");
        assertThat(wrapper.getContextPath()).isEqualTo("/t/acme");
        assertThat(wrapper.getServletPath()).isEqualTo("/api/v1/users/me");
        assertThat(wrapper.getRequestURI()).isEqualTo("/t/acme/api/v1/users/me");
        assertThat(RequestPathUtil.pathWithinApplication(wrapper)).isEqualTo("/api/v1/users/me");
    }

    @Test
    @DisplayName("기존 서블릿 contextPath 가 있으면 뒤에 이어 붙인다")
    void appendsToExistingContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/drive");
        request.setRequestURI("/drive/t/acme/mcp");
        request.setServletPath("/t/acme/mcp");

        TenantPathRequestWrapper wrapper = new TenantPathRequestWrapper(request, PREFIX);

        assertThat(wrapper.getContextPath()).isEqualTo("/drive/t/acme");
        assertThat(wrapper.getServletPath()).isEqualTo("/mcp");
    }

    @Test
    @DisplayName("prefix 만 있는 요청(/t/acme)은 servletPath 가 빈 문자열")
    void prefixOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/t/acme");
        request.setServletPath("/t/acme");

        TenantPathRequestWrapper wrapper = new TenantPathRequestWrapper(request, PREFIX);

        assertThat(wrapper.getServletPath()).isEmpty();
        assertThat(RequestPathUtil.pathWithinApplication(wrapper)).isEmpty();
    }

    @Test
    @DisplayName("prefix 가 다른 세그먼트의 접두(/t/acme-x)인 경우는 떼지 않는다")
    void doesNotStripPartialSegment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/t/acme-x/api");
        request.setServletPath("/t/acme-x/api");

        TenantPathRequestWrapper wrapper = new TenantPathRequestWrapper(request, PREFIX);

        assertThat(wrapper.getServletPath()).isEqualTo("/t/acme-x/api");
    }

    @Test
    @DisplayName("servletPath 가 비어 있으면(Mock) pathWithinApplication 은 requestURI − contextPath 로 계산된다")
    void fallsBackToRequestUri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/t/acme/oauth2/token");

        TenantPathRequestWrapper wrapper = new TenantPathRequestWrapper(request, PREFIX);

        assertThat(RequestPathUtil.pathWithinApplication(wrapper)).isEqualTo("/oauth2/token");
    }
}
