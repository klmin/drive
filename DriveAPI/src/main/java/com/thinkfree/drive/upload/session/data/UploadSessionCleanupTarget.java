package com.thinkfree.drive.upload.session.data;

import java.util.UUID;

/**
 * 상태를 FAILED 로 끝낸 세션의 정리 대상 — 조건부 UPDATE … RETURNING 으로 받는다(1행일 때만 정리 등록).
 *
 * @param uploadSessionId 세션 ID
 * @param storageKey      스토리지 key(준비 전이면 null)
 * @param uploadId        멀티파트 업로드 ID(단일 요청·준비 전이면 null)
 */
public record UploadSessionCleanupTarget
(
    UUID uploadSessionId,
    String storageKey,
    String uploadId
)
{
}
