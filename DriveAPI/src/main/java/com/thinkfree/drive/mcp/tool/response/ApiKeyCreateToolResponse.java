package com.thinkfree.drive.mcp.tool.response;

/**
 * API Key 발급 툴 응답 — 웹 ApiKeyCreateResponse 동형.
 * apiKey 는 발급된 키 원문으로, 이 응답에서만 볼 수 있다(목록 조회는 마스킹 값만 반환).
 */
public record ApiKeyCreateToolResponse(
        String apiKey
) {
}
