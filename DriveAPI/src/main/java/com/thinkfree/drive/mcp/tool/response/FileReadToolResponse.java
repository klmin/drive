package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 파일 읽기 결과 — text(본문)와 textUnavailableReason(추출 불가 사유)은 상호배타이고
 * webViewUrl(웹 미리보기 링크)은 항상 반환된다. 상호배타 탓에 null 노이즈가 커서 NON_NULL로 직렬화한다.
 * editUrl은 웹오피스 편집 가능 확장자이고 요청자에게 EDIT 권한이 있을 때만 실린다 —
 * 필드 유무가 곧 편집 가능 시그널이다(없으면 형식 미지원 또는 권한 없음).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileReadToolResponse
(
    String resourceName,
    String extension,
    Long sizeByte,
    String text,
    Integer offset,
    Integer returnedChars,
    Integer totalChars,
    Boolean truncated,
    String textUnavailableReason,
    String webViewUrl,
    String editUrl
)
{
}
