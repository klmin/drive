package com.thinkfree.drive.mcp.tool.response;

/**
 * 내부 파일 복제 API 명세와 동형 계약 — 필드는 웹 응답(FileCopyResponse)과 동일.
 * 웹 명세가 사본의 resourceSeq만 반환한다 — 사본 이름이 필요하면 get_metadata로 조회한다.
 */
public record FileCopyToolResponse
(
    Long resourceSeq
)
{
}
