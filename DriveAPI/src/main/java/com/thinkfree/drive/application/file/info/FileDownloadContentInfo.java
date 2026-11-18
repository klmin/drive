package com.thinkfree.drive.application.file.info;

/**
 * 파일 내용 다운로드 결과 — base64Content(원본 바이트의 base64)와
 * contentUnavailableReason(반환 불가 사유)은 상호배타. mimeType은 내용이 반환될 때만 채워진다.
 * ownerUserSeq는 스코프 소유자(ss??요청자) — 어댑터의 웹 링크(ss 파라미터) 조립용.
 */
public record FileDownloadContentInfo
(
    String resourceName,
    String extension,
    Long sizeByte,
    Integer ownerUserSeq,
    String mimeType,
    String base64Content,
    String contentUnavailableReason
)
{
}
