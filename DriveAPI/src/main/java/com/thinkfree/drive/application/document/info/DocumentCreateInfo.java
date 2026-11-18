package com.thinkfree.drive.application.document.info;

/**
 * 새 문서 생성 결과 — ownerUserSeq는 생성된 문서의 접근 스코프 사용자다
 * (웹 응답의 userSeq 재료이자 MCP webViewUrl의 ss 재료 — read/download Info 동형).
 */
public record DocumentCreateInfo
(
    Long resourceSeq,
    Integer ownerUserSeq
)
{
}
