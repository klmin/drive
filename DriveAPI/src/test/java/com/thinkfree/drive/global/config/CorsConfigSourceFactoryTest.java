package com.thinkfree.drive.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CORS — 쿠키 인증 경로만 허용 origin 으로 제한")
class CorsConfigSourceFactoryTest {

    private static final String ALLOWED = "http://localhost:3000";
    private static final String OTHER = "https://evil.example.com";

    private final CorsConfigurationSource source = CorsConfigSourceFactory.create(List.of(ALLOWED, " "));

    /** 브라우저가 다른 origin 에서 보내는 사전 요청(preflight)을 처리한 응답 */
    private MockHttpServletResponse preflight(String path, String origin) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.setServerName("drive.example.com");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Drive-Role");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration config = source.getCorsConfiguration(request);
        new DefaultCorsProcessor().processRequest(config, request, response);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/t/acme/api/v1/users", "/api/v1/token/reissue", "/oauth2/session"})
    @DisplayName("쿠키 인증 경로 — 등록한 origin 은 credentials 와 함께 허용")
    void cookiePathAllowsRegisteredOrigin(String path) throws IOException {
        MockHttpServletResponse response = preflight(path, ALLOWED);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/t/acme/api/v1/users"})
    @DisplayName("쿠키 인증 경로 — 등록하지 않은 origin 은 거부")
    void cookiePathRejectsOtherOrigin(String path) throws IOException {
        MockHttpServletResponse response = preflight(path, OTHER);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/public/v1/files", "/t/acme/api/public/v1/files", "/mcp", "/t/acme/mcp",
            "/.well-known/oauth-protected-resource/t/acme/mcp", "/oauth2/token", "/connect/register"})
    @DisplayName("API Key·OAuth 토큰 경로 — 모든 origin 허용, credentials 없음")
    void tokenOnlyPathAllowsAnyOriginWithoutCredentials(String path) throws IOException {
        MockHttpServletResponse response = preflight(path, OTHER);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(OTHER);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    @DisplayName("허용 목록이 비어 있으면 쿠키 인증 경로는 다른 origin 을 모두 거부")
    void emptyListRejectsAll() throws IOException {
        CorsConfigurationSource empty = CorsConfigSourceFactory.create(null);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/users");
        request.setServerName("drive.example.com");
        request.addHeader(HttpHeaders.ORIGIN, ALLOWED);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DefaultCorsProcessor().processRequest(empty.getCorsConfiguration(request), request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
