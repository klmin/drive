package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 파일 업로드(내용 저장) 결과 — resourceName은 중복 넘버링이 반영된 실제 저장 이름,
 * sizeByte는 디코딩 후 실제 저장 크기(LLM이 의도한 길이와 대조해 잘림을 감지하는 재료).
 * editUrl은 웹오피스 편집 가능 확장자이고 요청자에게 EDIT 권한이 있을 때만 실린다(read 동형 — 필드 유무가 시그널).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadToolResponse
(
    Long resourceSeq,
    String resourceName,
    Long sizeByte,
    String webViewUrl,
    String editUrl
)
{
}
