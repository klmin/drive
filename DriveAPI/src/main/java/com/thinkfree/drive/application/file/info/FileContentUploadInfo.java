package com.thinkfree.drive.application.file.info;

/**
 * drive_upload_file 결과 — resourceName은 중복 넘버링이 반영된 실제 저장 이름.
 * ownerUserSeq(접근 스코프)·extension은 어댑터의 링크 조립(webViewUrl/editUrl) 재료.
 */
public record FileContentUploadInfo
(
    Long resourceSeq,
    String resourceName,
    Long sizeByte,
    Integer ownerUserSeq,
    String extension
)
{
}
