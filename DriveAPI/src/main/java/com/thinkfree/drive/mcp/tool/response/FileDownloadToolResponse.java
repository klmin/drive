package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 파일 내용 다운로드 결과 — base64Content(원본 바이트의 base64)와 contentUnavailableReason(반환 불가
 * 사유)은 상호배타이고 webViewUrl(웹 링크)은 항상 반환된다. 상호배타 탓에 null 노이즈가 커서 NON_NULL로 직렬화한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileDownloadToolResponse
(
    String resourceName,
    String extension,
    Long sizeByte,
    String mimeType,
    String base64Content,
    String contentUnavailableReason,
    String webViewUrl
)
{
}
