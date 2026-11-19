package com.thinkfree.drive.api.v1.upload.response;

import java.time.Instant;
import java.util.List;

/**
 * 업로드 세션 상태.
 *
 * @param status        PENDING / COMPLETING / COMPLETED / FAILED
 * @param failReason    FAILED 사유(CANCELED·EXPIRED 등)
 * @param uploadedParts PENDING 일 때 올라간 파트 번호(크기가 맞는 것만) — 이어 올리기는 나머지만 보낸다. 목록 조회에서는 비운다
 * @param resourceSeq   COMPLETED 결과 파일
 * @param resourceName  COMPLETED 확정 이름(같은 이름이 있으면 넘버링된 이름)
 */
public record UploadSessionResponse
(
    String sessionId,
    String status,
    String failReason,
    String saveType,
    String fileName,
    long size,
    Long parentFolderSeq,
    Long targetResourceSeq,
    Long partSize,
    Integer partCount,
    List<Integer> uploadedParts,
    Long resourceSeq,
    String resourceName,
    Instant lastActivity,
    Instant createDate
)
{
}
