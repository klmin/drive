package com.thinkfree.drive.cleanup.storage.repository;

import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.thinkfree.drive.jooq.Tables.TB_STORAGE_DELETION;

/**
 * tb_storage_deletion 읽기·쓰기(jOOQ) — 엔티티 없이 다건 INSERT·조건 조회·IN 삭제·행별 UPDATE 배치만 한다.
 * 테넌트·사용자와 무관한 테이블이라 테넌트 조건이 없다.
 */
@Repository
@RequiredArgsConstructor
public class DefaultStorageDeletionRepository implements StorageDeletionRepository {

    private final DSLContext dsl;

    @Override
    public int insertAll(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return 0;
        }
        Instant now = JdbcAudit.now();
        int[] counts = JooqBatches.insert(dsl, storageKeys, key -> {
            var record = dsl.newRecord(TB_STORAGE_DELETION);
            record.setStorageKey(key);
            record.setAttemptCount(0);
            record.setNextAttemptDate(now);
            record.setRegisterDate(now);
            return record;
        });
        // JDBC 배치는 드라이버 설정에 따라 행 수 대신 SUCCESS_NO_INFO(-2)를 돌려줄 수 있다 — 실패면 예외라 음수도 1건으로 센다
        return IntStream.of(counts).map(c -> c < 0 ? 1 : c).sum();
    }

    @Override
    public List<StorageDeletionData> findDue(Instant now, int maxAttemptCount, int limit) {
        return dsl.select(TB_STORAGE_DELETION.STORAGE_DELETION_SEQ, TB_STORAGE_DELETION.STORAGE_KEY, TB_STORAGE_DELETION.ATTEMPT_COUNT)
                  .from(TB_STORAGE_DELETION)
                  .where(TB_STORAGE_DELETION.NEXT_ATTEMPT_DATE.le(now))
                  .and(TB_STORAGE_DELETION.ATTEMPT_COUNT.lt(maxAttemptCount))
                  .orderBy(TB_STORAGE_DELETION.STORAGE_DELETION_SEQ)
                  .limit(limit)
                  .fetch(r -> new StorageDeletionData(r.value1(), r.value2(), r.value3()));
    }

    @Override
    public int deleteBySeqList(List<Long> storageDeletionSeqList) {
        if (storageDeletionSeqList == null || storageDeletionSeqList.isEmpty()) {
            return 0;
        }
        return dsl.deleteFrom(TB_STORAGE_DELETION)
                  .where(TB_STORAGE_DELETION.STORAGE_DELETION_SEQ.in(storageDeletionSeqList))
                  .execute();
    }

    @Override
    public void markFailed(List<StorageDeletionFailQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }
        JooqBatches.batched(dsl, queries, (c, q) -> c.update(TB_STORAGE_DELETION)
                .set(TB_STORAGE_DELETION.ATTEMPT_COUNT, TB_STORAGE_DELETION.ATTEMPT_COUNT.plus(1))
                .set(TB_STORAGE_DELETION.NEXT_ATTEMPT_DATE, q.nextAttemptDate())
                .set(TB_STORAGE_DELETION.LAST_ERROR, q.lastError())
                .where(TB_STORAGE_DELETION.STORAGE_DELETION_SEQ.eq(q.storageDeletionSeq()))
                .execute());
    }
}
