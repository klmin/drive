package com.thinkfree.drive.restclient.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 호출(RestClient) 설정.
 * traceEnabled — 요청·응답 본문을 버퍼링해 응답 본문까지 로그에 남긴다. 서버 기본 false, 로컬은 DriveAPI/.env 에서 true.
 */
@ConfigurationProperties("drive.rest-client")
public record RestClientProperty
(
    boolean traceEnabled
)
{
}
