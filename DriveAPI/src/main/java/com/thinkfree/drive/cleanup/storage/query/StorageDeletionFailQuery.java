package com.thinkfree.drive.cleanup.storage.query;

import java.time.Instant;

/**
 * 삭제 실패 한 건 — 시도 횟수를 올리고 다음 시도 시각·실패 사유를 남긴다.
 *
 * @param storageDeletionSeq 대기열 seq
 * @param nextAttemptDate    다음 시도 가능 시각(백오프 적용)
 * @param lastError          실패 사유(컬럼 길이에 맞춰 자른 값)
 */
public record StorageDeletionFailQuery
(
    Long storageDeletionSeq,
    Instant nextAttemptDate,
    String lastError
)
{
}
