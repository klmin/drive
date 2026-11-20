package com.thinkfree.drive.httpclient.guard;

import com.sun.net.httpserver.HttpServer;
import com.thinkfree.drive.httpclient.HttpClientProperties;
import com.thinkfree.drive.httpclient.config.HttpClientConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 로컬 HTTP 서버(127.0.0.1)로 가드 클라이언트의 차단·허용·리다이렉트 미추종을 확인한다. */
class GuardedHttpClientTest {

    private static final HttpClientResponseHandler<Integer> STATUS = HttpResponse::getCode;

    private HttpServer server;
    private final AtomicInteger internalHits = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "/internal");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/internal", ex -> { internalHits.incrementAndGet(); ex.sendResponseHeaders(200, -1); ex.close(); });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static GuardedHttpClient client(boolean enabled, String... allowed) {
        var guard = new OutboundAddressGuard(new OutboundGuardProperties(enabled, List.of(allowed)));
        return new HttpClientConfig().guardedHttpClient(HttpClientProperties.defaults(), guard);
    }

    @Test
    void blocksLoopbackWhenEnabled() {
        assertThatThrownBy(() -> client(true).client().execute(new HttpGet(url("/ok")), STATUS))
                .isInstanceOf(OutboundAddressGuard.BlockedAddressException.class);
    }

    @Test
    void allowsListedCidr() throws Exception {
        assertThat(client(true, "127.0.0.0/8").client().execute(new HttpGet(url("/ok")), STATUS)).isEqualTo(200);
    }

    @Test
    void allowsWhenDisabled() throws Exception {
        assertThat(client(false).client().execute(new HttpGet(url("/ok")), STATUS)).isEqualTo(200);
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        assertThat(client(false).client().execute(new HttpGet(url("/redirect")), STATUS)).isEqualTo(302);
        assertThat(internalHits).hasValue(0);
    }
}
