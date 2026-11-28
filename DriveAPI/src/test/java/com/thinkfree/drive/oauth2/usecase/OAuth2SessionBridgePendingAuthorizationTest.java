package com.thinkfree.drive.oauth2.usecase;

import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 세션 브리지의 SavedRequest 판정 — authorize 경로를 정확히 비교한다(문자열 포함 검사 금지). */
@DisplayName("OAuth2SessionBridgeUseCase — SavedRequest authorize 경로 판정")
class OAuth2SessionBridgePendingAuthorizationTest {

    private static SavedRequest saved(String requestUri, String query) {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", requestUri);
        original.setQueryString(query);
        return new DefaultSavedRequest(original);
    }

    private static boolean pending(SavedRequest savedRequest, HttpServletRequest bridgeRequest) {
        return OAuth2SessionBridgeUseCase.isPendingAuthorization(savedRequest, bridgeRequest);
    }

    @Test
    @DisplayName("authorize 요청(쿼리 포함)이면 통과")
    void authorizeRequest() {
        SavedRequest savedRequest = saved("/oauth2/authorize", "response_type=code&client_id=c&code_challenge=x");

        assertThat(pending(savedRequest, new MockHttpServletRequest("POST", "/oauth2/session"))).isTrue();
    }

    @Test
    @DisplayName("쿼리스트링이나 다른 경로에 /oauth2/authorize 가 들어간 요청은 거부")
    void containsButNotAuthorize() {
        MockHttpServletRequest bridge = new MockHttpServletRequest("POST", "/oauth2/session");

        assertThat(pending(saved("/api/v1/files", "next=/oauth2/authorize"), bridge)).isFalse();
        assertThat(pending(saved("/x/oauth2/authorize", null), bridge)).isFalse();
        assertThat(pending(saved("/oauth2/authorize/extra", null), bridge)).isFalse();
        assertThat(pending(saved("/oauth2/authorizefoo", null), bridge)).isFalse();
    }

    @Test
    @DisplayName("SavedRequest 없음은 거부")
    void none() {
        assertThat(pending(null, new MockHttpServletRequest())).isFalse();
    }

    @Test
    @DisplayName("PATH 테넌트 모드 — /t/{tenant} 접두어까지 일치해야 통과, 다른 테넌트 접두어는 거부")
    void pathTenantMode() {
        HttpServletRequest bridge = new TenantPathRequestWrapper(
                new MockHttpServletRequest("POST", "/t/acme/oauth2/session"), "/t/acme");

        assertThat(pending(saved("/t/acme/oauth2/authorize", "client_id=c"), bridge)).isTrue();
        assertThat(pending(saved("/t/other/oauth2/authorize", "client_id=c"), bridge)).isFalse();
        assertThat(pending(saved("/oauth2/authorize", "client_id=c"), bridge)).isFalse();
    }
}
