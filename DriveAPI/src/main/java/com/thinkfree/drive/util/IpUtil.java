package com.thinkfree.drive.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 요청 클라이언트 IP.
 *
 * <p>{@code X-Forwarded-For}·{@code X-Real-IP} 같은 헤더를 직접 읽지 않는다. 클라이언트가 마음대로 넣을 수 있어
 * IP 접근 제한·슈퍼관리자 IP 가드를 헤더 하나로 우회할 수 있기 때문이다.
 * 프록시 뒤 실제 IP 는 {@code server.forward-headers-strategy: native}(Tomcat RemoteIpValve)가
 * 신뢰 프록시({@code server.tomcat.remoteip.internal-proxies}·{@code trusted-proxies})에서 온 요청의
 * {@code X-Forwarded-For} 만 오른쪽부터 풀어 {@link HttpServletRequest#getRemoteAddr()} 에 넣어 준다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IpUtil {

    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    public static String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (LOCALHOST_IPV4.equals(remoteAddr) || LOCALHOST_IPV6.equals(remoteAddr)) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ignored) {
                return remoteAddr;
            }
        }

        return remoteAddr;
    }
}
