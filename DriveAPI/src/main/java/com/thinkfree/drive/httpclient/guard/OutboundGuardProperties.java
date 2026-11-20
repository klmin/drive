package com.thinkfree.drive.httpclient.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 사용자·테넌트 관리자가 입력한 주소(OIDC issuer, JWT JWKS, SMTP host)로 나가는 요청의 내부망 가드.
 * <ul>
 *     <li>{@code enabled} — true 면 loopback·사설망·CGNAT·IPv6 ULA 등 내부 대역을 차단한다. 기본 false(기존 동작 유지).</li>
 *     <li>{@code allowedCidrs} — enabled 일 때 예외로 허용할 내부 대역(사내 IdP·메일 서버). 공인 IP 는 이 목록과 상관없이 통과.</li>
 * </ul>
 * 링크로컬(169.254.0.0/16 클라우드 메타데이터, fe80::/10)은 설정과 상관없이 항상 차단한다.
 */
@ConfigurationProperties("drive.outbound-guard")
public record OutboundGuardProperties(
        boolean enabled,
        List<String> allowedCidrs
) {
    public OutboundGuardProperties {
        allowedCidrs = allowedCidrs == null
                ? List.of()
                : allowedCidrs.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
    }
}
