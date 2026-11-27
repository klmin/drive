package com.thinkfree.drive.global.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 요청 수 제한 설정 — 분당 횟수(토큰 버킷 용량, 1분에 걸쳐 고르게 다시 채움). 시작값이라 운영하며 조정한다.
 *
 * @param enabled              끄면 제한하지 않는다
 * @param loginPerAccount      로그인 — 같은 아이디(테넌트·화면별) 분당. 관리자·슈퍼관리자 화면은 잠금이 없어 이것이 무차별 대입을 막는다
 * @param loginPerIp           로그인 — 같은 IP(테넌트별) 분당. 회사 NAT 뒤 여러 사용자가 한 IP 로 보일 수 있어 넉넉히
 * @param anonymousLoginPerIp  공유 링크 익명 로그인 — 같은 IP 분당(링크 토큰 추측 방지)
 */
@ConfigurationProperties("drive.rate-limit")
public record RateLimitProperty(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") long loginPerAccount,
        @DefaultValue("100") long loginPerIp,
        @DefaultValue("30") long anonymousLoginPerIp
) {
}
