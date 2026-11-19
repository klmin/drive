package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * 세션 정리 스케줄러 본체(docs/design/upload-session-redesign.md §9.3) — 트랜잭션 없이 묶음 단위 트랜잭션({@link UploadSessionCleanupService})을 반복한다.
 * <table>
 *   <tr><th>대상</th><th>조건</th><th>처리</th></tr>
 *   <tr><td>PENDING</td><td>last_activity &lt; now() - TTL</td><td>FAILED(EXPIRED) + ABORT</td></tr>
 *   <tr><td>COMPLETING</td><td>completing_at &lt; now() - 30m</td><td>FAILED(COMMIT_ERROR) + ABORT + DELETE</td></tr>
 *   <tr><td>COMPLETED / FAILED</td><td>create_date &lt; now() - 7d</td><td>행 삭제</td></tr>
 * </table>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSessionCleanupProcessor {

    /** 한 실행에서 단계별로 돌릴 최대 묶음 수 — 적체가 커도 한 실행이 잠금 시간을 넘지 않게 */
    static final int MAX_BATCHES_PER_STEP = 100;

    private final UploadSessionCleanupService uploadSessionCleanupService;
    private final UploadSessionProperty property;

    public record Result(int expired, int staleCompleting, int purged) {
        public boolean isEmpty() {
            return expired == 0 && staleCompleting == 0 && purged == 0;
        }
    }

    public Result run() {
        int expired = repeat(uploadSessionCleanupService::expirePendingBatch);
        int staleCompleting = repeat(uploadSessionCleanupService::failStaleCompletingBatch);
        if (staleCompleting > 0) {
            // 정상이면 생기지 않는다 — complete 처리 중 서버 다운·커밋 결과 불명확
            log.warn("[UploadSessionCleanupProcessor] COMPLETING timed out sessions: {}", staleCompleting);
        }
        int purged = repeat(uploadSessionCleanupService::purgeFinishedBatch);
        return new Result(expired, staleCompleting, purged);
    }

    private int repeat(IntSupplier batch) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_STEP; i++) {
            int count = batch.getAsInt();
            total += count;
            if (count < property.cleanupBatchSize()) {
                break;
            }
        }
        return total;
    }
}
