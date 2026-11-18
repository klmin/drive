package com.thinkfree.drive.mcp.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** RFC 9728 Protected Resource Metadata 응답 — 필드명은 스펙 고정(snake_case, JsonNaming으로 일괄 변환). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record McpProtectedResourceMetadataResponse(
        String resource,
        List<String> authorizationServers,
        List<String> bearerMethodsSupported,
        List<String> scopesSupported
) {
}
