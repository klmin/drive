package com.thinkfree.drive.oauth2.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 동의 화면 redirect 목적지 표시 — 경로·쿼리는 빼고 host[:port] 나 앱 스킴만 남긴다. */
@DisplayName("OAuth2ConsentInfoController — redirect 목적지 표시값")
class OAuth2ConsentInfoRedirectHostsTest {

    @Test
    @DisplayName("https 는 호스트, 루프백 http 는 host:port, 앱 스킴은 scheme://host")
    void hosts() {
        assertThat(OAuth2ConsentInfoController.redirectHosts(Set.of("https://claude.ai/api/mcp/auth_callback?x=1")))
                .containsExactly("claude.ai");
        assertThat(OAuth2ConsentInfoController.redirectHosts(Set.of("http://127.0.0.1:53682/callback")))
                .containsExactly("127.0.0.1:53682");
        assertThat(OAuth2ConsentInfoController.redirectHosts(Set.of("cursor://anysphere.cursor-retrieval/oauth/callback")))
                .containsExactly("cursor://anysphere.cursor-retrieval");
        assertThat(OAuth2ConsentInfoController.redirectHosts(Set.of("com.example.app:/oauth2redirect")))
                .containsExactly("com.example.app:");
    }

    @Test
    @DisplayName("같은 목적지는 한 번만, 정렬된 순서로")
    void dedupAndOrder() {
        assertThat(OAuth2ConsentInfoController.redirectHosts(Set.of(
                "https://evil.example.com/a", "https://evil.example.com/b", "https://claude.ai/cb")))
                .containsExactly("claude.ai", "evil.example.com");
    }
}
