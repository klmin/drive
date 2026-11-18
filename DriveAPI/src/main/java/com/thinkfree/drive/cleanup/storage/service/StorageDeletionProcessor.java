package com.thinkfree.drive.cleanup.storage.service;

import com.thinkfree.drive.cleanup.storage.constants.StorageDeletionConstants;
import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 삭제 대기열 처리 — 처리할 행을 {@link StorageDeletionConstants#BATCH_SIZE} 개씩 꺼내 스토리지에서 지우고 결과를 반영한다.
 * <p>
 * 트랜잭션을 걸지 않는다: 스토리지 호출 동안 DB 트랜잭션·커넥션을 잡지 않도록 조회·반영만 각각 짧은 트랜잭션({@link StorageDeletionService}).
 * 스토리지 삭제는 멱등이라(없는 키도 성공) 같은 키를 다시 처리해도 무해하다 — 반영 전에 죽으면 다음 실행이 다시 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageDeletionProcessor {

    private final StorageDeletionService storageDeletionService;
    private final StorageService storageService;

    /**
     * 처리할 행이 없거나 {@link StorageDeletionConstants#RUN_TIME_BUDGET} 이 지날 때까지 반복한다.
     *
     * @return 이번 실행에서 지운(대기열에서 뺀) 행 수
     */
    public int processDue() {
        Instant deadline = Instant.now().plus(StorageDeletionConstants.RUN_TIME_BUDGET);
        int deletedCount = 0;
        while (Instant.now().isBefore(deadline)) {
            List<StorageDeletionData> rows = storageDeletionService.findDue(Instant.now(), StorageDeletionConstants.MAX_ATTEMPT_COUNT, StorageDeletionConstants.BATCH_SIZE);
            if (rows.isEmpty()) {
                break;
            }
            deletedCount += processBatch(rows);
            if (rows.size() < StorageDeletionConstants.BATCH_SIZE) {
                break;
            }
        }
        return deletedCount;
    }

    private int processBatch(List<StorageDeletionData> rows) {
        // 같은 키가 여러 행일 수 있다 — 스토리지에는 한 번만 보내고 결과는 그 키의 행 모두에 반영
        Map<String, List<StorageDeletionData>> rowsByKey = new LinkedHashMap<>();
        for (StorageDeletionData row : rows) {
            rowsByKey.computeIfAbsent(row.getStorageKey(), k -> new ArrayList<>()).add(row);
        }

        StorageDeleteObjectsResult result = storageService.deleteObjectsReportingFailures(new ArrayList<>(rowsByKey.keySet()));

        Instant now = Instant.now();
        List<Long> succeededSeqList = new ArrayList<>();
        List<StorageDeletionFailQuery> failQueries = new ArrayList<>();
        for (var entry : rowsByKey.entrySet()) {
            String key = entry.getKey();
            if (!result.isFailed(key)) {
                entry.getValue().forEach(row -> succeededSeqList.add(row.getStorageDeletionSeq()));
                continue;
            }
            String error = truncate(result.failedKeys().get(key));
            for (StorageDeletionData row : entry.getValue()) {
                failQueries.add(new StorageDeletionFailQuery(row.getStorageDeletionSeq(), now.plus(backoffMinutes(row.getAttemptCount()), ChronoUnit.MINUTES), error));
                if (row.getAttemptCount() + 1 >= StorageDeletionConstants.MAX_ATTEMPT_COUNT) {
                    log.warn("[StorageDeletionProcessor exhausted] seq: {}, key: {}, error: {}", row.getStorageDeletionSeq(), key, error);
                }
            }
        }

        storageDeletionService.applyResult(succeededSeqList, failQueries);
        if (!failQueries.isEmpty()) {
            log.warn("[StorageDeletionProcessor processBatch] succeeded: {}, failed: {}", succeededSeqList.size(), failQueries.size());
        }
        return succeededSeqList.size();
    }

    /**
     * 2^(이전 실패 횟수) 분, 상한 {@link StorageDeletionConstants#MAX_BACKOFF_MINUTES}.
     * 시프트 칸 수를 6(= 64분 ≥ 상한)으로 먼저 묶는다 — 자바 long 시프트는 칸 수를 64 로 나눈 나머지만 써서 63 이상이면 음수·1 로 되돌아간다.
     */
    static long backoffMinutes(int attemptCount) {
        return Math.min(1L << Math.min(attemptCount, 6), StorageDeletionConstants.MAX_BACKOFF_MINUTES);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= StorageDeletionConstants.LAST_ERROR_MAX_LENGTH ? error : error.substring(0, StorageDeletionConstants.LAST_ERROR_MAX_LENGTH);
    }
}
