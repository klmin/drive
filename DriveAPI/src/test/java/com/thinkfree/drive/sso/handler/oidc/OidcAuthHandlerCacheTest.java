package com.thinkfree.drive.sso.handler.oidc;

import com.github.benmanes.caffeine.cache.Ticker;
import com.sun.net.httpserver.HttpServer;
import com.thinkfree.drive.restclient.config.RestClientFactory;
import com.thinkfree.drive.sso.handler.params.OidcCallbackParams;
import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OIDC 디스커버리·디코더 캐시 — 만료, 실패 시 다시 받기, 디코더 키. 로컬 HTTP 서버를 IdP 로. */
@DisplayName("OidcAuthHandler — 디스커버리 캐시 만료·실패 시 갱신")
class OidcAuthHandlerCacheTest {

    private HttpServer idp;
    private String base;
    private final AtomicInteger discoveryHits = new AtomicInteger();
    private final AtomicReference<String> authorizationEndpoint = new AtomicReference<>("/authorize");
    private final AtomicLong now = new AtomicLong();
    private OidcAuthHandler handler;
    private SsoOidcConfigData config;

    @BeforeEach
    void startIdp() throws IOException {
        idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + idp.getAddress().getPort();
        idp.createContext("/.well-known/openid-configuration", exchange -> {
            discoveryHits.incrementAndGet();
            String json = "{\"issuer\":\"" + base + "\",\"authorization_endpoint\":\"" + base + authorizationEndpoint.get()
                    + "\",\"token_endpoint\":\"" + base + "/token\",\"jwks_uri\":\"" + base + "/jwks\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        idp.createContext("/token", exchange -> {
            exchange.sendResponseHeaders(500, -1); // 토큰 엔드포인트가 옮겨 갔다고 치자
            exchange.close();
        });
        idp.start();

        RestClientFactory factory = mock(RestClientFactory.class);
        when(factory.createGuardedRestClient(anyString())).thenAnswer(inv -> RestClient.create((String) inv.getArgument(0)));
        Ticker ticker = now::get;
        handler = new OidcAuthHandler(factory, ticker);
        config = SsoOidcConfigData.builder().ssoProviderSeq(1).issuerUrl(base).clientId("drive").clientSecret("test-secret").build();
    }

    @AfterEach
    void stopIdp() {
        idp.stop(0);
    }

    private String authorizationUrl() {
        return handler.buildAuthorizationUrl(config, "state", "https://drive.test/callback");
    }

    @Test
    @DisplayName("디스커버리는 TTL 동안 한 번만 받고, 지나면 다시 받아 엔드포인트 변경이 반영된다")
    void discoveryExpires() {
        assertThat(authorizationUrl()).startsWith(base + "/authorize");
        authorizationUrl();
        assertThat(discoveryHits).hasValue(1);

        authorizationEndpoint.set("/v2/authorize");
        now.addAndGet(OidcAuthHandler.DISCOVERY_TTL.toNanos() + 1);

        assertThat(authorizationUrl()).startsWith(base + "/v2/authorize");
        assertThat(discoveryHits).hasValue(2);
    }

    @Test
    @DisplayName("토큰 교환이 실패하면 디스커버리를 버려 다음 시도에서 다시 받는다")
    void failureEvictsDiscovery() {
        authorizationUrl();
        assertThat(discoveryHits).hasValue(1);

        assertThatThrownBy(() -> handler.handleCallback(null, config, new OidcCallbackParams("code", "https://drive.test/callback")))
                .isInstanceOf(RuntimeException.class);

        authorizationUrl();
        assertThat(discoveryHits).as("TTL 전이지만 실패 뒤라 다시 받음").hasValue(2);
    }

    @Test
    @DisplayName("디스커버리 실패는 캐시하지 않는다 — IdP 가 돌아오면 바로 성공")
    void discoveryFailureNotCached() {
        SsoOidcConfigData down = SsoOidcConfigData.builder().ssoProviderSeq(2).issuerUrl(base + "/missing").clientId("drive").build();
        assertThatThrownBy(() -> handler.buildAuthorizationUrl(down, "s", "https://drive.test/callback"))
                .isInstanceOf(RuntimeException.class);
        assertThat(authorizationUrl()).startsWith(base);
    }

    @Test
    @DisplayName("디코더 키에 issuer 가 들어간다 — issuer 만 바뀌어도 새 검증기")
    void decoderKeyIncludesIssuer() {
        assertThat(OidcAuthHandler.decoderKey("https://a", "https://jwks", "c"))
                .isNotEqualTo(OidcAuthHandler.decoderKey("https://b", "https://jwks", "c"));
    }
}
