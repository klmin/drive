package com.thinkfree.drive.mcp.tool.response;

/**
 * 내부 중요문서함 등록 API 명세와 동형 계약 — 웹 응답(StarredSaveResponse)과 동일한 단일 필드.
 * 봉투({data, error})만 MCP 규약.
 */
public record StarredSaveToolResponse
(
    Long starredSeq
)
{
}
