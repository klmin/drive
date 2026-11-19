package com.thinkfree.drive.upload.session.data;

/**
 * 사용자의 진행 중(PENDING·COMPLETING) 세션 합계 — 세션 시작 용량 사전 검사·사용자당 동시 세션 수·화면의 uploadingByte.
 *
 * @param sizeByte 신고 크기 합계(byte)
 * @param count    세션 수
 */
public record UploadSessionActiveUsage
(
    long sizeByte,
    int count
)
{
}
