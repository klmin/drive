package com.thinkfree.drive.oauth2.service;

import java.util.List;

public interface OAuth2ClientRegistrationService {

    /**
     * DCR 클라이언트 등록 — 등록 정책(confidential + PKCE 필수 + 토큰 TTL + secret 해싱)을
     * 적용해 RegisteredClient를 생성·저장하고, 평문 secret과 함께 반환한다.
     *
     * @param clientName   클라이언트 표시명 (null/공백이면 client_id로 대체)
     * @param redirectUris 유효성 검증이 끝난 redirect URI 목록 (1개 이상)
     * @param scopes       요청 scope 목록 (없으면 빈 리스트)
     */
    OAuth2ClientRegistrationResult register(String clientName, List<String> redirectUris, List<String> scopes);
}
