package com.thinkfree.drive.mcp.tool.response;

/**
 * 내부 폴더 업로드 API 명세와 동형 계약 — 필드는 웹 응답(FolderUploadResponse)과 동일.
 * resourceName은 실제 생성된 이름 — 동명 폴더가 있으면 서버가 번호를 붙여 요청 이름과 다를 수 있다.
 */
public record FolderCreateToolResponse
(
    Long resourceSeq,
    String resourceName
)
{
}
