package com.thinkfree.drive.mcp.util;

import com.thinkfree.drive.mcp.constants.McpConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpUrls.baseUrl — 요청 기준 베이스 URL + contextPath")
class McpUrlsTest {

    @Test
    @DisplayName("기본 포트는 생략, contextPath 없으면 authority 만")
    void defaultPortNoContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("default.drive.thinkfree.com");
        request.setServerPort(443);

        assertThat(McpUrls.baseUrl(request)).isEqualTo("https://default.drive.thinkfree.com");
    }

    @Test
    @DisplayName("비표준 포트는 붙인다")
    void nonDefaultPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        assertThat(McpUrls.baseUrl(request)).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("PATH 테넌트 모드(래퍼 contextPath=/t/acme)면 뒤에 붙어 SAS issuer 와 같은 형태가 된다 — RS 메타는 접두형(issuer + /.well-known/…/mcp)")
    void appendsContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("drive.thinkfree.com");
        request.setServerPort(443);
        request.setContextPath("/t/acme");

        assertThat(McpUrls.baseUrl(request)).isEqualTo("https://drive.thinkfree.com/t/acme");
        assertThat(McpUrls.protectedResourceMetadataUrl(request))
                .isEqualTo("https://drive.thinkfree.com/t/acme" + McpConstants.PROTECTED_RESOURCE_METADATA_MCP_PATH);
    }

    @Test
    @DisplayName("X-Forwarded-Host/Proto 우선 + contextPath")
    void forwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("10.0.0.5");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Host", "drive.thinkfree.com, internal");
        request.addHeader("X-Forwarded-Proto", "https");
        request.setContextPath("/acme");

        assertThat(McpUrls.baseUrl(request)).isEqualTo("https://drive.thinkfree.com/acme");
    }
}
