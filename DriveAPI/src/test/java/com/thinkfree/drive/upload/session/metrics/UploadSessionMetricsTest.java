package com.thinkfree.drive.upload.session.metrics;

import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("업로드 세션 지표")
class UploadSessionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UploadSessionMetrics metrics = new UploadSessionMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private double ended(String result, String reason) {
        return registry.get(UploadSessionMetrics.ENDED).tag("result", result).tag("reason", reason).counter().count();
    }

    private long startedCount(String kind) {
        return registry.get(UploadSessionMetrics.SIZE).tag("kind", kind).summary().count();
    }

    @Test
    @DisplayName("모든 태그 조합을 시작할 때 0 으로 만들어 둔다")
    void preRegistersAllSeries() {
        assertThat(startedCount("chunk")).isZero();
        assertThat(startedCount("server")).isZero();
        assertThat(ended("completed", "none")).isZero();
        for (UploadSessionFailReason reason : UploadSessionFailReason.values()) {
            assertThat(ended("failed", reason.name().toLowerCase())).isZero();
        }
    }

    @Test
    @DisplayName("partCount 가 있으면 chunk, 없으면 server — 크기를 함께 남긴다")
    void startedByKind() {
        metrics.started(3, 40L * 1024 * 1024);
        metrics.started(null, 100);

        assertThat(startedCount("chunk")).isEqualTo(1);
        assertThat(registry.get(UploadSessionMetrics.SIZE).tag("kind", "chunk").summary().totalAmount()).isEqualTo(40d * 1024 * 1024);
        assertThat(startedCount("server")).isEqualTo(1);
    }

    @Test
    @DisplayName("종료 — completed 와 실패 이유별, 0건은 무시")
    void ended() {
        metrics.completed();
        metrics.failed(UploadSessionFailReason.EXPIRED, 3);
        metrics.failed(UploadSessionFailReason.CANCELED, 0);

        assertThat(ended("completed", "none")).isEqualTo(1);
        assertThat(ended("failed", "expired")).isEqualTo(3);
        assertThat(ended("failed", "canceled")).isZero();
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋된 뒤에만 센다 — 롤백되면 세지 않는다")
    void countsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.completed();
        metrics.failed(UploadSessionFailReason.COMMIT_ERROR, 1);
        assertThat(ended("completed", "none")).isZero();

        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        assertThat(ended("completed", "none")).isEqualTo(1);
        assertThat(ended("failed", "commit_error")).isEqualTo(1);

        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        metrics.completed();
        TransactionSynchronizationManager.getSynchronizations()
                                         .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(ended("completed", "none")).isEqualTo(1);
    }
}
