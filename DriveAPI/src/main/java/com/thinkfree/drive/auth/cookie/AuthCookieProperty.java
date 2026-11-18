package com.thinkfree.drive.auth.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로그인 토큰 쿠키 속성 중 배포 환경마다 달라지는 값. 그 외 속성은 {@link AuthCookieManager} 가 고정한다
 * (HttpOnly 항상 true, Domain 지정 안 함, Max-Age 는 {@link com.thinkfree.drive.auth.properties.JwtProperty}).
 *
 * @param secure   HTTPS 요청에만 전송. TLS 가 jar 앞단 리버스 프록시에서 끝나 {@code request.isSecure()} 에 맡기지 않고 명시한다
 *                 (운영 true, 로컬 http 개발 false)
 * @param sameSite 다른 사이트에서 시작된 요청에 실을지. FE·BE 를 서로 다른 사이트로 배포하면 {@code None}(+secure) 필요
 */
@ConfigurationProperties("auth.cookie")
public record AuthCookieProperty
(
        @DefaultValue("true") boolean secure,
        @DefaultValue("Lax") String sameSite
)
{
}
