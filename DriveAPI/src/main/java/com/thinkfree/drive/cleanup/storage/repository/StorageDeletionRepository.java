package com.thinkfree.drive.cleanup.storage.repository;

import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;

import java.time.Instant;
import java.util.List;

/** tb_storage_deletion(스토리지 객체 삭제 대기열) 읽기·쓰기. */
public interface StorageDeletionRepository {

    /**
     * 키마다 한 행을 넣는다 — 시도 횟수 0, 다음 시도 시각·등록 시각 = 지금(JdbcAudit.now(), 초 절삭).
     *
     * @return 넣은 행 수
     */
    int insertAll(List<String> storageKeys);

    /** 지금 처리할 행 — 다음 시도 시각이 지났고 시도 횟수가 상한 미만, seq 순으로 limit 개. */
    List<StorageDeletionData> findDue(Instant now, int maxAttemptCount, int limit);

    /** @return 지운 행 수 */
    int deleteBySeqList(List<Long> storageDeletionSeqList);

    /** 행마다 attempt_count + 1, next_attempt_date·last_error 갱신. */
    void markFailed(List<StorageDeletionFailQuery> queries);
}
