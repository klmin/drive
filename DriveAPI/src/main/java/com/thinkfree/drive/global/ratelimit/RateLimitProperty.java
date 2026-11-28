package com.thinkfree.drive.global.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 요청 수 제한 설정 — 토큰 버킷 용량(기간에 걸쳐 고르게 다시 채움). 시작값이라 운영하며 조정한다.
 *
 * @param enabled                      끄면 제한하지 않는다
 * @param loginPerAccount              로그인 — 같은 아이디(테넌트·화면별) 분당. 관리자·슈퍼관리자 화면은 잠금이 없어 이것이 무차별 대입을 막는다
 * @param loginPerIp                   로그인 — 같은 IP(테넌트별) 분당. 회사 NAT 뒤 여러 사용자가 한 IP 로 보일 수 있어 넉넉히
 * @param anonymousLoginPerIp          공유 링크 익명 로그인 — 같은 IP 분당(링크 토큰 추측 방지)
 * @param mfaPerUser                   MFA 코드 확인(로그인 2단계·첫 설정 확인) — 같은 사용자 분당. 틀린 MFA 코드는 로그인 잠금에 세지 않아 이것이 OTP 추측을 막는다
 * @param passwordResetLinkPerAccount  재설정 링크 메일 요청 — 같은 계정 15분당(남의 메일함 폭주 방지)
 * @param passwordResetLinkPerIp       재설정 링크 메일 요청 — 같은 IP(테넌트별) 분당
 * @param apiKeyPerKey                 API Key 하나(MCP OAuth 는 사용자·연동 하나) 분당 — 순간 이만큼, 평균 초당 값/60. 외부·내부·/mcp 체인이 한 버킷을 나눠 쓴다
 */
@ConfigurationProperties("drive.rate-limit")
public record RateLimitProperty(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") long loginPerAccount,
        @DefaultValue("100") long loginPerIp,
        @DefaultValue("30") long anonymousLoginPerIp,
        @DefaultValue("5") long mfaPerUser,
        @DefaultValue("3") long passwordResetLinkPerAccount,
        @DefaultValue("20") long passwordResetLinkPerIp,
        @DefaultValue("600") long apiKeyPerKey
) {
}
