package com.thinkfree.drive.upload.session.metrics;

import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 업로드 세션 지표(Prometheus) — 상태를 바꾸는 저장소({@code DefaultUploadSessionRepository})가 부른다.
 * <ul>
 *   <li>{@code drive_upload_session_size_bytes_{count,sum,max}}{kind} — 시작한 세션 수·신고 크기.
 *       kind = chunk(청크 업로드 API) / server(기존 업로드 API·복사·템플릿 등 서버 내부 쓰기)</li>
 *   <li>{@code drive_upload_session_ended_total}{result, reason} — 끝난 세션. result = completed / failed,
 *       reason = 실패 이유(소문자 {@link UploadSessionFailReason}) 또는 none</li>
 * </ul>
 * 진행 중 세션 수는 DB 상태라 여기서 세지 않는다 — Alloy 의 postgres exporter 커스텀 쿼리(observability/alloy/pg-queries.yaml).
 * <p>
 * 트랜잭션 안에서 부르면 커밋된 뒤에 센다(롤백·재시도된 변경을 세지 않게). 태그는 모두 값 종류가 고정된 것만 — 사용자·테넌트·세션 ID 는 넣지 않는다.
 * 모든 조합을 시작할 때 0 으로 만들어 둔다 — 처음 생긴 시계열의 첫 증가를 {@code increase()} 가 놓치지 않게.
 */
@Component
public class UploadSessionMetrics {

    static final String SIZE = "drive.upload.session.size";
    static final String ENDED = "drive.upload.session.ended";

    private final DistributionSummary chunkSize;
    private final DistributionSummary serverSize;
    private final Counter completed;
    private final Map<UploadSessionFailReason, Counter> failed = new EnumMap<>(UploadSessionFailReason.class);

    public UploadSessionMetrics(MeterRegistry registry) {
        this.chunkSize = size(registry, "chunk");
        this.serverSize = size(registry, "server");
        this.completed = ended(registry, "completed", "none");
        for (UploadSessionFailReason reason : UploadSessionFailReason.values()) {
            failed.put(reason, ended(registry, "failed", reason.name().toLowerCase()));
        }
    }

    /** 세션 생성. partCount 가 있으면 청크 업로드 API 세션이다. */
    public void started(Integer partCount, long sizeByte) {
        DistributionSummary summary = partCount != null ? chunkSize : serverSize;
        afterCommit(() -> summary.record(sizeByte));
    }

    public void completed() {
        afterCommit(completed::increment);
    }

    public void failed(UploadSessionFailReason reason, int count) {
        if (count <= 0) {
            return;
        }
        Counter counter = failed.get(reason);
        afterCommit(() -> counter.increment(count));
    }

    private static DistributionSummary size(MeterRegistry registry, String kind) {
        return DistributionSummary.builder(SIZE)
                                  .description("Upload sessions started and their declared size")
                                  .baseUnit("bytes")
                                  .tag("kind", kind)
                                  .register(registry);
    }

    private static Counter ended(MeterRegistry registry, String result, String reason) {
        return Counter.builder(ENDED)
                      .description("Upload sessions that reached COMPLETED or FAILED")
                      .tag("result", result)
                      .tag("reason", reason)
                      .register(registry);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
