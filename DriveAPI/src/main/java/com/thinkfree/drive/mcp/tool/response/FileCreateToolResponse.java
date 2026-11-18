package com.thinkfree.drive.mcp.tool.response;

/**
 * 새 문서 생성 결과 — 내부 새 문서 생성 API 명세(resourceSeq) + webViewUrl(웹오피스 편집 진입 링크).
 * 웹 명세가 seq만 반환한다 — 생성된 문서명이 필요하면 get_metadata로 조회한다.
 * userSeq(스코프)는 webViewUrl 재료로만 쓰이고 응답에서 제외한다(FileReadToolResponse 선례).
 */
public record FileCreateToolResponse
(
    Long resourceSeq,
    String webViewUrl
)
{
}
