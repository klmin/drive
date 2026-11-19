package com.thinkfree.drive.upload.session.model;

import com.thinkfree.drive.storage.constants.StorageConstants;

/**
 * 파트 계획(서버가 결정 — docs/design/upload-session-redesign.md §5.4).
 * <ul>
 *   <li>partSize = max(minPartSize, ceil(size / 10,000))</li>
 *   <li>partCount = ceil(size / partSize) — 0바이트는 멀티파트 불가라 단일 요청으로 보낸다(여기서는 1로 둔다)</li>
 *   <li>n번 기대 크기 = n &lt; count ? partSize : size - partSize * (count - 1)</li>
 * </ul>
 * S3 는 파트 크기를 기대값과 비교하지 않으므로(5MB 미만 비마지막만 EntityTooSmall) 서버가 파트 수신 시·complete 시 직접 검증한다.
 * 파트 크기 검증 덕분에 총 크기가 신고 크기를 넘을 수 없다.
 *
 * @param size      신고 크기(byte)
 * @param partSize  파트 크기(byte)
 * @param partCount 파트 수
 */
public record UploadPartPlan
(
    long size,
    long partSize,
    int partCount
)
{
    public static UploadPartPlan of(long size, long minPartSize) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        if (minPartSize < StorageConstants.MIN_PART_SIZE) {
            throw new IllegalArgumentException("minPartSize must be >= " + StorageConstants.MIN_PART_SIZE + ": " + minPartSize);
        }
        long partSize = Math.max(minPartSize, ceilDiv(size, StorageConstants.MAX_PART_COUNT));
        long partCount = size == 0 ? 1 : ceilDiv(size, partSize);
        if (partCount > StorageConstants.MAX_PART_COUNT) {
            // partSize >= ceil(size / 10,000) 이므로 도달하지 않는다 — 계산 실수 방어
            throw new IllegalStateException("partCount exceeds limit: " + partCount);
        }
        return new UploadPartPlan(size, partSize, (int) partCount);
    }

    /** n번(1부터) 파트의 기대 크기. 범위 밖이면 -1. */
    public long expectedPartSize(int partNumber) {
        if (partNumber < 1 || partNumber > partCount) {
            return -1;
        }
        if (partNumber < partCount) {
            return partSize;
        }
        return size - partSize * (partCount - 1L);
    }

    private static long ceilDiv(long a, long b) {
        return a == 0 ? 0 : (a + b - 1) / b;
    }
}
