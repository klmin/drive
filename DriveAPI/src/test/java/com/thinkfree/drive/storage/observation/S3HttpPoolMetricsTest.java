package com.thinkfree.drive.storage.observation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.HttpMetric;
import software.amazon.awssdk.metrics.MetricCollector;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3HttpPoolMetrics — SDK 지표 묶음에서 풀 지표 기록")
class S3HttpPoolMetricsTest {

    @Test
    @DisplayName("시도마다 HttpClient 하위 묶음의 대기 시간·빌린 수·대기 수를 기록한다")
    void recordsEveryAttempt() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        S3HttpPoolMetrics metrics = new S3HttpPoolMetrics(registry, 250);

        MetricCollector apiCall = MetricCollector.create("ApiCall");
        attempt(apiCall, Duration.ofMillis(5), 10, 0);
        attempt(apiCall, Duration.ofMillis(300), 250, 7);
        metrics.publish(apiCall.collect());

        assertThat(registry.timer(S3HttpPoolMetrics.ACQUIRE).count()).isEqualTo(2);
        assertThat(registry.timer(S3HttpPoolMetrics.ACQUIRE).max(TimeUnit.MILLISECONDS)).isEqualTo(300.0);
        assertThat(registry.summary(S3HttpPoolMetrics.LEASED).max()).isEqualTo(250.0);
        assertThat(registry.summary(S3HttpPoolMetrics.PENDING).max()).isEqualTo(7.0);
        assertThat(registry.get(S3HttpPoolMetrics.MAX_CONNECTIONS).gauge().value()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("HttpClient 묶음이 없는 시도(연결 전 실패 등)는 건너뛴다")
    void ignoresAttemptWithoutHttpClient() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        S3HttpPoolMetrics metrics = new S3HttpPoolMetrics(registry, 50);

        MetricCollector apiCall = MetricCollector.create("ApiCall");
        apiCall.createChild("ApiCallAttempt");
        metrics.publish(apiCall.collect());

        assertThat(registry.timer(S3HttpPoolMetrics.ACQUIRE).count()).isZero();
        assertThat(registry.summary(S3HttpPoolMetrics.LEASED).count()).isZero();
    }

    private static void attempt(MetricCollector apiCall, Duration acquire, int leased, int pending) {
        MetricCollector http = apiCall.createChild("ApiCallAttempt").createChild("HttpClient");
        http.reportMetric(HttpMetric.CONCURRENCY_ACQUIRE_DURATION, acquire);
        http.reportMetric(HttpMetric.LEASED_CONCURRENCY, leased);
        http.reportMetric(HttpMetric.PENDING_CONCURRENCY_ACQUIRES, pending);
    }
}
