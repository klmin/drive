package com.thinkfree.drive.oauth2.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * RFC 7591 DCR 등록 요청 바디 — 필드명은 스펙 고정(snake_case, JsonNaming으로 일괄 매핑).
 * 스펙상 클라이언트가 임의 메타데이터 필드를 더 보낼 수 있으므로(예: token_endpoint_auth_method)
 * 선언 안 된 필드는 무시된다 (Spring Boot 기본 ObjectMapper — FAIL_ON_UNKNOWN_PROPERTIES=false).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OAuth2ClientRegistrationRequest(
        String clientName,
        List<String> redirectUris,
        String scope
) {
}
