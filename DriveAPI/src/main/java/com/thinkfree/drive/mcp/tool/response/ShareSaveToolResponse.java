package com.thinkfree.drive.mcp.tool.response;

/**
 * drive_share_resource 툴 응답 — 등록(웹 ShareSaveResponse 동형 축약)과 수정(웹은 본문 없음)을 하나로 합친
 * 툴 전용 계약. created로 등록/수정 중 어느 경로였는지 알리고, 링크 공유(ORG_LINK/PUBLIC_LINK)면
 * 웹 '링크 복사'와 동일 경로로 만든 접속 링크(linkUrl)를 함께 준다 (SPECIFIC_USERS는 null).
 */
public record ShareSaveToolResponse
(
    Long shareSeq,
    Long resourceSeq,
    String shareType,
    Boolean created,
    String linkUrl
)
{
}
