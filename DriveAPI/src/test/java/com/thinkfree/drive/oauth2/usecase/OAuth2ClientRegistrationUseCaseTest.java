package com.thinkfree.drive.oauth2.usecase;

import com.thinkfree.drive.oauth2.exception.InvalidClientRegistrationException;
import com.thinkfree.drive.oauth2.request.OAuth2ClientRegistrationRequest;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationResult;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DCR redirect_uri 허용 정책 — https, 루프백 http, private-use 스킴만 허용하고 개수·총길이 상한을 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2ClientRegistrationUseCase — redirect_uri 정책")
class OAuth2ClientRegistrationUseCaseTest {

    @Mock
    private OAuth2ClientRegistrationService oauth2ClientRegistrationService;

    @InjectMocks
    private OAuth2ClientRegistrationUseCase useCase;

    private static OAuth2ClientRegistrationRequest request(List<String> redirectUris) {
        return new OAuth2ClientRegistrationRequest("client", redirectUris, null, "none");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://claude.ai/api/mcp/auth_callback",
            "http://localhost:33418/callback",
            "http://127.0.0.1:6274/oauth/callback",
            "http://[::1]:8080/cb",
            "HTTP://LOCALHOST/cb",
            "cursor://anysphere.cursor-retrieval/oauth/callback",
            "com.example.app:/oauth2redirect"
    })
    @DisplayName("허용 — https · 루프백 http · private-use 스킴")
    void allowed(String uri) {
        RegisteredClient client = RegisteredClient.withId("id")
                .clientId("client-id")
                .clientIdIssuedAt(Instant.now())
                .clientName("client")
                .clientSecret("{noop}x")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(uri)
                .scope("mcp:read")
                .build();
        given(oauth2ClientRegistrationService.register(eq("client"), eq(List.of(uri)), anyList()))
                .willReturn(new OAuth2ClientRegistrationResult(client, "secret"));

        assertThat(useCase.register(request(List.of(uri)))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.example.com/cb",
            "http://localhost.evil.com/cb",
            "javascript:alert(1)",
            "JavaScript://x/%0aalert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "https://claude.ai/cb#frag",
            "https:///no-host",
            "/relative/callback",
            "not a uri",
            ""
    })
    @DisplayName("거부 — 비루프백 http · 위험 스킴 · fragment · 상대경로 · 형식 오류")
    void rejected(String uri) {
        assertThatThrownBy(() -> useCase.register(request(List.of(uri))))
                .isInstanceOf(InvalidClientRegistrationException.class);
        verify(oauth2ClientRegistrationService, never()).register(any(), anyList(), anyList());
    }

    @Test
    @DisplayName("거부 — redirect_uris 누락·빈 목록")
    void missing() {
        assertThatThrownBy(() -> useCase.register(request(null)))
                .isInstanceOf(InvalidClientRegistrationException.class);
        assertThatThrownBy(() -> useCase.register(request(List.of())))
                .isInstanceOf(InvalidClientRegistrationException.class);
    }

    @Test
    @DisplayName("거부 — 11개 이상, 또는 쉼표 연결 총길이가 컬럼(varchar 1000)을 넘으면 400")
    void tooManyOrTooLong() {
        List<String> eleven = Collections.nCopies(11, "https://claude.ai/cb");
        assertThatThrownBy(() -> useCase.register(request(eleven)))
                .isInstanceOf(InvalidClientRegistrationException.class);

        String longUri = "https://claude.ai/" + "a".repeat(1000);
        assertThatThrownBy(() -> useCase.register(request(List.of(longUri))))
                .isInstanceOf(InvalidClientRegistrationException.class);

        verify(oauth2ClientRegistrationService, never()).register(any(), anyList(), anyList());
    }
}
