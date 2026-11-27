package com.thinkfree.drive.util;

import jakarta.servlet.ServletRequest;
import org.apache.catalina.filters.RemoteIpFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpUtil — 클라이언트가 보낸 IP 헤더를 믿지 않는다")
class IpUtilTest {

    private static final String PUBLIC_CLIENT = "198.51.100.7";
    private static final String ALLOWED_IP = "203.0.113.9";

    @Test
    @DisplayName("X-Forwarded-For·X-Real-IP 등 헤더가 있어도 remoteAddr 를 쓴다")
    void ignoresClientSuppliedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PUBLIC_CLIENT);
        request.addHeader("X-Forwarded-For", ALLOWED_IP);
        request.addHeader("X-Real-IP", ALLOWED_IP);
        request.addHeader("Proxy-Client-IP", ALLOWED_IP);
        request.addHeader("HTTP_CLIENT_IP", ALLOWED_IP);

        assertThat(IpUtil.extractClientIp(request)).isEqualTo(PUBLIC_CLIENT);
    }

    // server.forward-headers-strategy: native 가 켜는 RemoteIpValve 와 같은 로직(RemoteIpFilter)·같은 기본 신뢰 범위로
    // IpUtil 이 받게 될 remoteAddr 를 확인한다

    @Test
    @DisplayName("신뢰 프록시(사설 대역)를 거친 요청은 X-Forwarded-For 의 클라이언트 IP 가 된다")
    void trustedProxyForwardedIpIsUsed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.17.0.1");
        request.addHeader("X-Forwarded-For", ALLOWED_IP);

        assertThat(clientIpThroughRemoteIp(request)).isEqualTo(ALLOWED_IP);
    }

    @Test
    @DisplayName("공인 IP 가 직접 보낸 X-Forwarded-For 는 무시된다")
    void directPublicClientCannotSpoof() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PUBLIC_CLIENT);
        request.addHeader("X-Forwarded-For", ALLOWED_IP);

        assertThat(clientIpThroughRemoteIp(request)).isEqualTo(PUBLIC_CLIENT);
    }

    @Test
    @DisplayName("프록시가 덧붙인 경우 위조한 앞쪽 값이 아니라 프록시가 본 클라이언트 IP 가 된다")
    void spoofedPrefixBeforeProxyAppendIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.17.0.1");
        request.addHeader("X-Forwarded-For", ALLOWED_IP + ", " + PUBLIC_CLIENT);

        assertThat(clientIpThroughRemoteIp(request)).isEqualTo(PUBLIC_CLIENT);
    }

    private static String clientIpThroughRemoteIp(MockHttpServletRequest request) throws Exception {
        RemoteIpFilter remoteIp = new RemoteIpFilter();
        remoteIp.init(new MockFilterConfig());
        AtomicReference<String> seen = new AtomicReference<>();
        remoteIp.doFilter(request, new MockHttpServletResponse(),
                (ServletRequest req, jakarta.servlet.ServletResponse res) ->
                        seen.set(IpUtil.extractClientIp((jakarta.servlet.http.HttpServletRequest) req)));
        return seen.get();
    }
}
