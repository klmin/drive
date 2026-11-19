package com.thinkfree.drive.upload.session.data;

import com.thinkfree.drive.upload.session.enums.UploadSaveType;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;

import java.time.Instant;
import java.util.UUID;

/** tb_upload_session 한 행. storageKey·uploadId 는 스토리지 준비 전이면 null, uploadId 는 단일 요청이면 늘 null. */
public record UploadSessionData
(
    UUID uploadSessionId,
    Integer tenantSeq,
    int userSeq,
    int requestUserSeq,
    UploadSaveType saveType,
    Long parentFolderSeq,
    Long resourceSeq,
    Long sourceVersionSeq,
    String baseStorageKey,
    String fileName,
    String contentType,
    long sizeByte,
    String storageKey,
    String uploadId,
    Long partSize,
    Integer partCount,
    UploadSessionStatus status,
    UploadSessionFailReason failReason,
    Long resultResourceSeq,
    String resultName,
    Instant lastActivity,
    Instant completingAt,
    Instant createDate
)
{
    public boolean isMultipart() {
        return uploadId != null;
    }
}
