package com.thinkfree.drive.httpclient.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 외부 요청 대상 IP 판정 — 문자열이 아니라 DNS 로 얻은 실제 IP 로 판단한다({@code 0x7f000001}, 내부 IP 를 가리키는 도메인 등 우회 방지).
 * <ol>
 *     <li>링크로컬(169.254.0.0/16, fe80::/10) — 항상 차단</li>
 *     <li>가드 꺼짐 — 그 밖은 모두 통과</li>
 *     <li>내부 대역(loopback·any-local·사설망·멀티캐스트·브로드캐스트·100.64.0.0/10·fc00::/7) — allowedCidrs 에 있으면 통과, 없으면 차단</li>
 *     <li>그 밖(공인 IP) — 통과</li>
 * </ol>
 */
@Slf4j
@Component
public class OutboundAddressGuard {

    private static final IpAddressMatcher CGNAT = new IpAddressMatcher("100.64.0.0/10");
    private static final IpAddressMatcher IPV6_ULA = new IpAddressMatcher("fc00::/7");
    private static final IpAddressMatcher IPV4_THIS_NETWORK = new IpAddressMatcher("0.0.0.0/8");
    private static final IpAddressMatcher IPV4_BROADCAST = new IpAddressMatcher("255.255.255.255/32");

    private final boolean enabled;
    private final List<IpAddressMatcher> allowed;

    public OutboundAddressGuard(OutboundGuardProperties properties) {
        this.enabled = properties.enabled();
        this.allowed = properties.allowedCidrs().stream().map(IpAddressMatcher::new).toList();
    }

    /** 호스트를 DNS 조회해 모든 IP 를 검사한다. 하나라도 차단 대상이면 {@link BlockedAddressException}. */
    public InetAddress[] resolveAndVerify(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (!isAllowed(address)) {
                log.warn("[OutboundGuard] 내부 주소로의 요청 차단. host : {}, address : {}", host, address.getHostAddress());
                throw new BlockedAddressException(host);
            }
        }
        return addresses;
    }

    public boolean isAllowed(InetAddress address) {
        if (address.isLinkLocalAddress()) {
            return false;
        }
        if (!enabled || !isInternal(address)) {
            return true;
        }
        String ip = address.getHostAddress();
        return allowed.stream().anyMatch(m -> m.matches(ip));
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        String ip = address.getHostAddress();
        if (address instanceof Inet4Address) {
            return CGNAT.matches(ip) || IPV4_THIS_NETWORK.matches(ip) || IPV4_BROADCAST.matches(ip);
        }
        return address instanceof Inet6Address && IPV6_ULA.matches(ip);
    }

    /** 차단 — HttpClient DnsResolver 계약에 맞춰 UnknownHostException 으로 던진다(RestClient 에서는 ResourceAccessException). */
    public static class BlockedAddressException extends UnknownHostException {
        public BlockedAddressException(String host) {
            super("outbound request to internal address blocked: " + host);
        }
    }
}
