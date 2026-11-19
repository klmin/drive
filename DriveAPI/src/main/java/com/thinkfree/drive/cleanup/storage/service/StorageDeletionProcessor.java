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
import java.util.Set;

/**
 * 정리 대기열 처리 — 같은 스케줄러 작업 안에서 action 별로 경로를 나눈다.
 * <ul>
 *   <li>DELETE — {@link StorageDeletionConstants#BATCH_SIZE} 개씩 꺼내 key 별로 묶어 DeleteObjects 일괄 삭제</li>
 *   <li>ABORT_MULTIPART — {@link StorageDeletionConstants#ABORT_BATCH_SIZE} 개씩 꺼내 행마다 abort(NoSuchUpload = 성공)</li>
 * </ul>
 * 섞지 않는 이유: DELETE 의 key 묶음 처리에 같은 key 의 ABORT 행이 합쳐지면 abort 가 빠진다.
 * 실행 시간 예산은 DELETE 가 앞 절반, ABORT 가 남은 시간을 쓰고, 시간이 남으면 DELETE 를 이어 한다(한쪽 적체가 다른 쪽을 굶기지 않게).
 * <p>
 * 트랜잭션을 걸지 않는다: 스토리지 호출 동안 DB 트랜잭션·커넥션을 잡지 않도록 조회·반영만 각각 짧은 트랜잭션({@link StorageDeletionService}).
 * 스토리지 삭제·abort 는 멱등이라 같은 행을 다시 처리해도 무해하다 — 반영 전에 죽으면 다음 실행이 다시 한다.
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
     * @return 이번 실행에서 처리 완료한(대기열에서 뺀) 행 수
     */
    public int processDue() {
        Instant start = Instant.now();
        Instant deadline = start.plus(StorageDeletionConstants.RUN_TIME_BUDGET);
        Instant half = start.plus(StorageDeletionConstants.RUN_TIME_BUDGET.dividedBy(2));
        DeleteRun first = processDueDeletes(half);
        int processed = first.processed();
        processed += processDueAborts(deadline);
        // 앞 절반에서 시간이 모자라 끝나지 못했을 때만 이어 한다(대기열이 비어 끝났으면 다시 조회하지 않는다)
        if (first.hasMore() && Instant.now().isBefore(deadline)) {
            processed += processDueDeletes(deadline).processed();
        }
        return processed;
    }

    /** DELETE 처리 결과 — hasMore: 시간 예산에 걸려 멈춤(남은 행이 있을 수 있음). */
    record DeleteRun(int processed, boolean hasMore) {
    }

    /** DELETE 행 처리 — deadline 까지. */
    DeleteRun processDueDeletes(Instant deadline) {
        int deletedCount = 0;
        while (Instant.now().isBefore(deadline)) {
            List<StorageDeletionData> rows = storageDeletionService.findDue(Instant.now(), StorageDeletionConstants.MAX_ATTEMPT_COUNT, StorageDeletionConstants.BATCH_SIZE);
            if (rows.isEmpty()) {
                return new DeleteRun(deletedCount, false);
            }
            deletedCount += processDeleteBatch(rows);
            if (rows.size() < StorageDeletionConstants.BATCH_SIZE) {
                return new DeleteRun(deletedCount, false);
            }
        }
        return new DeleteRun(deletedCount, true);
    }

    /** ABORT_MULTIPART 행 처리 — deadline 까지. */
    int processDueAborts(Instant deadline) {
        int abortedCount = 0;
        while (Instant.now().isBefore(deadline)) {
            List<StorageDeletionData> rows = storageDeletionService.findDueAborts(Instant.now(), StorageDeletionConstants.MAX_ATTEMPT_COUNT, StorageDeletionConstants.ABORT_BATCH_SIZE);
            if (rows.isEmpty()) {
                break;
            }
            abortedCount += processAbortBatch(rows);
            if (rows.size() < StorageDeletionConstants.ABORT_BATCH_SIZE) {
                break;
            }
        }
        return abortedCount;
    }

    private int processDeleteBatch(List<StorageDeletionData> rows) {
        // 같은 키가 여러 행일 수 있다 — 스토리지에는 한 번만 보내고 결과는 그 키의 행 모두에 반영
        Map<String, List<StorageDeletionData>> rowsByKey = new LinkedHashMap<>();
        for (StorageDeletionData row : rows) {
            rowsByKey.computeIfAbsent(row.getStorageKey(), k -> new ArrayList<>()).add(row);
        }

        Instant now = Instant.now();
        List<Long> succeededSeqList = new ArrayList<>();
        List<StorageDeletionFailQuery> failQueries = new ArrayList<>();

        // 삭제 직전 방어 — 아직 리소스·버전 행이 참조하는 key 는 지우지 않는다(지우면 데이터 유실). 실패로 남겨 시도 상한에서 수동 확인
        Set<String> referenced = storageDeletionService.findReferencedKeys(rowsByKey.keySet());
        if (referenced != null) {
            for (String key : referenced) {
                List<StorageDeletionData> keyRows = rowsByKey.remove(key);
                if (keyRows == null) {
                    continue;
                }
                log.warn("[StorageDeletionProcessor skip referenced key] key: {}, rows: {}", key, keyRows.size());
                addFailures(failQueries, keyRows, key, StorageDeletionConstants.REFERENCED_ERROR, now);
            }
        }

        StorageDeleteObjectsResult result = rowsByKey.isEmpty()
                ? new StorageDeleteObjectsResult(Map.of())
                : storageService.deleteObjectsReportingFailures(new ArrayList<>(rowsByKey.keySet()));

        for (var entry : rowsByKey.entrySet()) {
            String key = entry.getKey();
            if (!result.isFailed(key)) {
                entry.getValue().forEach(row -> succeededSeqList.add(row.getStorageDeletionSeq()));
                continue;
            }
            addFailures(failQueries, entry.getValue(), key, truncate(result.failedKeys().get(key)), now);
        }

        storageDeletionService.applyResult(succeededSeqList, failQueries);
        if (!failQueries.isEmpty()) {
            log.warn("[StorageDeletionProcessor processDeleteBatch] succeeded: {}, failed: {}", succeededSeqList.size(), failQueries.size());
        }
        return succeededSeqList.size();
    }

    private int processAbortBatch(List<StorageDeletionData> rows) {
        Instant now = Instant.now();
        List<Long> succeededSeqList = new ArrayList<>();
        List<StorageDeletionFailQuery> failQueries = new ArrayList<>();
        for (StorageDeletionData row : rows) {
            try {
                // NoSuchUpload 는 구현이 성공으로 처리(멱등)
                storageService.abortMultipartUpload(row.getStorageKey(), row.getUploadId());
                succeededSeqList.add(row.getStorageDeletionSeq());
            } catch (RuntimeException e) {
                String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage());
                addFailures(failQueries, List.of(row), row.getStorageKey(), error, now);
            }
        }
        storageDeletionService.applyResult(succeededSeqList, failQueries);
        if (!failQueries.isEmpty()) {
            log.warn("[StorageDeletionProcessor processAbortBatch] succeeded: {}, failed: {}", succeededSeqList.size(), failQueries.size());
        }
        return succeededSeqList.size();
    }

    private static void addFailures(List<StorageDeletionFailQuery> failQueries, List<StorageDeletionData> rows, String key, String error, Instant now) {
        for (StorageDeletionData row : rows) {
            failQueries.add(new StorageDeletionFailQuery(row.getStorageDeletionSeq(), now.plus(backoffMinutes(row.getAttemptCount()), ChronoUnit.MINUTES), error));
            if (row.getAttemptCount() + 1 >= StorageDeletionConstants.MAX_ATTEMPT_COUNT) {
                log.warn("[StorageDeletionProcessor exhausted] seq: {}, key: {}, error: {}", row.getStorageDeletionSeq(), key, error);
            }
        }
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
