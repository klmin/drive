package com.thinkfree.drive.httpclient.guard;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 연결 직전 DNS 조회에서 가드를 적용 — 검사한 IP 로만 연결되므로 검사·연결 사이 DNS 바꿔치기(rebinding)도 막힌다.
 */
@RequiredArgsConstructor
public class GuardedDnsResolver implements DnsResolver {

    private final OutboundAddressGuard guard;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return guard.resolveAllowed(host);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }
}
