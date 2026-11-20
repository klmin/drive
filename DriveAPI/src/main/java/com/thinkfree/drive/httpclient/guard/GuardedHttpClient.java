package com.thinkfree.drive.httpclient.guard;

import org.apache.hc.client5.http.classic.HttpClient;

/**
 * 사용자 입력 주소 전용 HttpClient — 기본 {@code HttpClient} 빈(운영자 설정 주소용)과 타입으로 구분하려고 감싼다.
 * 내부망 가드 DNS 리졸버 + 리다이렉트 비활성.
 */
public record GuardedHttpClient(HttpClient client) {
}
