package com.thinkfree.drive.oauth2.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OAuth2 AS가 발급하는 토큰의 수명 정책 (application.yml `oauth2.token.*`).
 * DCR 등록 시 RegisteredClient의 TokenSettings로 저장되어, 이후 /oauth2/token 발급 때 적용된다
 * — 값을 바꿔도 "이미 등록된" 클라이언트에는 소급되지 않는다 (행에 저장된 설정 우선).
 */
@ConfigurationProperties("oauth2.token")
public record OAuth2TokenProperty(
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
