package com.thinkfree.drive.cleanup.storage.repository;

import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** tb_storage_deletion(스토리지 객체 삭제 대기열) 읽기·쓰기. */
public interface StorageDeletionRepository {

    /**
     * 키마다 DELETE 행 하나 — 시도 횟수 0, 다음 시도 시각·등록 시각 = 지금(JdbcAudit.now(), 초 절삭).
     *
     * @return 넣은 행 수
     */
    int insertAll(List<String> storageKeys);

    /**
     * 키마다 DELETE 행 하나 — 다음 시도 시각을 nextAttemptDate 로(교체·밀려난 이전 key 의 삭제 유예).
     *
     * @return 넣은 행 수
     */
    int insertDeferred(List<String> storageKeys, Instant nextAttemptDate);

    /** ABORT_MULTIPART 행 하나 — 다음 시도 시각·등록 시각 = 지금. */
    void insertAbort(String storageKey, String uploadId);

    /** 지금 처리할 DELETE 행 — 다음 시도 시각이 지났고 시도 횟수가 상한 미만, seq 순으로 limit 개. */
    List<StorageDeletionData> findDue(Instant now, int maxAttemptCount, int limit);

    /** 지금 처리할 ABORT_MULTIPART 행 — 조건은 {@link #findDue} 와 같다. */
    List<StorageDeletionData> findDueAborts(Instant now, int maxAttemptCount, int limit);

    /** 주어진 키 중 tb_resource·tb_resource_version 이 아직 참조하는 키 — 삭제 직전 방어용. */
    Set<String> findReferencedKeys(Collection<String> storageKeys);

    /** @return 지운 행 수 */
    int deleteBySeqList(List<Long> storageDeletionSeqList);

    /** 행마다 attempt_count + 1, next_attempt_date·last_error 갱신. */
    void markFailed(List<StorageDeletionFailQuery> queries);
}
