package com.thinkfree.drive.oauth2.service;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * DCR 등록 결과 — 저장된 클라이언트 + 평문 secret.
 * secret은 BCrypt 해싱 전 원문으로, RFC 7591 등록 응답에 한 번 실어주는 용도 (재조회 불가).
 */
public record OAuth2ClientRegistrationResult(RegisteredClient registeredClient, String clientSecret) {
}
