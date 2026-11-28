package com.thinkfree.drive.oauth2.config;

import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** access 토큰 aud = [client_id, issuer + /mcp] 고정, client_id 클레임 (RFC 8707 audience binding). */
@DisplayName("OAuth2AuthorizationServerConfig — access 토큰 audience 커스터마이저")
class OAuth2TokenCustomizerTest {

    private static final String CLIENT_ID = "client-1";

    private final OAuth2TokenCustomizer<JwtEncodingContext> customizer =
            new OAuth2AuthorizationServerConfig(null).oauth2TokenCustomizer();

    private static JwtEncodingContext context(OAuth2TokenType tokenType, String issuer, JwtClaimsSet.Builder claims) {
        RegisteredClient client = RegisteredClient.withId("id")
                .clientId(CLIENT_ID)
                .clientSecret("{noop}x")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://claude.ai/cb")
                .build();
        AuthorizationServerContext serverContext = new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return issuer;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().build();
            }
        };
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .registeredClient(client)
                .principal(new TestingAuthenticationToken("user", null))
                .authorizationServerContext(serverContext)
                .tokenType(tokenType)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
    }

    private static JwtClaimsSet.Builder baseClaims() {
        return JwtClaimsSet.builder().subject("10").audience(List.of(CLIENT_ID)).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    @DisplayName("access 토큰 — aud 에 client_id 와 테넌트 issuer 기준 /mcp 리소스, client_id 클레임")
    void accessTokenAudience() {
        JwtClaimsSet.Builder claims = baseClaims();

        customizer.customize(context(OAuth2TokenType.ACCESS_TOKEN, "https://a.drive.example.com", claims));

        JwtClaimsSet built = claims.build();
        assertThat(built.getAudience()).containsExactly(CLIENT_ID, "https://a.drive.example.com/mcp");
        assertThat(built.getClaimAsString(OAuth2Constants.CLAIMS_CLIENT_ID)).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("클레임이 인가 저장소 매퍼(allowlist)로 저장·복원된다 — refresh 시 역직렬화 실패 회귀 방지")
    void claimsSurviveAuthorizationMapperRoundTrip() throws Exception {
        JwtClaimsSet.Builder claims = baseClaims();
        customizer.customize(context(OAuth2TokenType.ACCESS_TOKEN, "https://a.drive.example.com", claims));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(JdbcOAuth2AuthorizationService.class.getClassLoader()));
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        // JdbcOAuth2AuthorizationService 가 토큰 메타데이터(클레임 포함)를 Map 으로 쓰고 읽는 방식과 같다
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("metadata.token.claims", new HashMap<>(claims.build().getClaims()));
        String json = mapper.writeValueAsString(metadata);

        Map<String, Object> restored = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        Map<String, Object> restoredClaims = (Map<String, Object>) restored.get("metadata.token.claims");
        assertThat(restoredClaims.get("aud")).isEqualTo(List.of(CLIENT_ID, "https://a.drive.example.com/mcp"));
    }

    @Test
    @DisplayName("PATH 테넌트 모드 issuer(/t/acme) 도 그대로 이어 붙인다")
    void pathTenantIssuer() {
        JwtClaimsSet.Builder claims = baseClaims();

        customizer.customize(context(OAuth2TokenType.ACCESS_TOKEN, "https://drive.example.com/t/acme", claims));

        assertThat(claims.build().getAudience()).contains("https://drive.example.com/t/acme/mcp");
    }

    @Test
    @DisplayName("access 토큰이 아니면 손대지 않는다")
    void nonAccessTokenUntouched() {
        JwtClaimsSet.Builder claims = baseClaims();

        customizer.customize(context(new OAuth2TokenType("id_token"), "https://a.drive.example.com", claims));

        JwtClaimsSet built = claims.build();
        assertThat(built.getAudience()).containsExactly(CLIENT_ID);
        assertThat(built.hasClaim(OAuth2Constants.CLAIMS_CLIENT_ID)).isFalse();
    }
}
