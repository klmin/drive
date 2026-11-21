package com.thinkfree.drive.storage.observation;

import com.thinkfree.drive.storage.exception.S3ConnectionPoolTimeout;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.HttpMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;

import java.time.Duration;

/**
 * S3 동기 클라이언트 HTTP 커넥션 풀 지표({@code drive.s3.http.*}).
 * <ul>
 *   <li>{@code drive.s3.http.acquire} 타이머(히스토그램) — 풀에서 연결을 빌리는 데 걸린 시간. <b>주 지표</b>: 대기가 생기면 바로 오른다</li>
 *   <li>{@code drive.s3.http.acquire.timeout} 카운터 — 연결을 못 빌려 실패한 API 호출 수(재시도하지 않으므로 = 503 수). <b>주 지표</b></li>
 *   <li>{@code drive.s3.http.leased}, {@code drive.s3.http.pending} 분포 — 시도마다 그 순간 빌려 간 연결 수·대기 중인 요청 수.
 *       게이지로 두면 요청이 끝날 때만 갱신돼 부하가 끝나도 마지막 값에 멈추고 동시 요청끼리 덮어쓰므로 분포로 남긴다
 *       (패널은 {@code max_over_time(..._max[1m])})</li>
 *   <li>{@code drive.s3.http.max.connections} 게이지 — 설정한 풀 크기</li>
 * </ul>
 * 값은 SDK 가 API 호출마다 넘기는 {@link MetricCollection}(ApiCall → ApiCallAttempt → HttpClient)에서 읽는다.
 * 대기 초과는 지표 묶음에 오류 종류가 남지 않아 실패 인터셉터로 센다.
 */
public class S3HttpPoolMetrics implements MetricPublisher, ExecutionInterceptor {

    public static final String ACQUIRE = "drive.s3.http.acquire";
    public static final String ACQUIRE_TIMEOUT = "drive.s3.http.acquire.timeout";
    public static final String LEASED = "drive.s3.http.leased";
    public static final String PENDING = "drive.s3.http.pending";
    public static final String MAX_CONNECTIONS = "drive.s3.http.max.connections";

    private static final String ATTEMPT = "ApiCallAttempt";
    private static final String HTTP_CLIENT = "HttpClient";

    private final Timer acquire;
    private final Counter acquireTimeout;
    private final DistributionSummary leased;
    private final DistributionSummary pending;

    public S3HttpPoolMetrics(MeterRegistry registry, int maxConnections) {
        this.acquire = Timer.builder(ACQUIRE)
                .description("S3 HTTP 커넥션 풀에서 연결을 빌리는 데 걸린 시간")
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);
        this.acquireTimeout = Counter.builder(ACQUIRE_TIMEOUT)
                .description("S3 HTTP 커넥션 풀 대기 초과로 실패한 호출 수")
                .register(registry);
        this.leased = DistributionSummary.builder(LEASED)
                .description("시도 시점에 빌려 간 S3 HTTP 연결 수")
                .register(registry);
        this.pending = DistributionSummary.builder(PENDING)
                .description("시도 시점에 연결을 기다리던 S3 요청 수")
                .register(registry);
        Gauge.builder(MAX_CONNECTIONS, () -> maxConnections)
                .description("S3 HTTP 커넥션 풀 최대 연결 수(설정값)")
                .register(registry);
    }

    @Override
    public void publish(MetricCollection apiCall) {
        apiCall.childrenWithName(ATTEMPT)
               .flatMap(attempt -> attempt.childrenWithName(HTTP_CLIENT))
               .forEach(this::record);
    }

    private void record(MetricCollection http) {
        http.metricValues(HttpMetric.CONCURRENCY_ACQUIRE_DURATION).forEach(acquire::record);
        http.metricValues(HttpMetric.LEASED_CONCURRENCY).forEach(leased::record);
        http.metricValues(HttpMetric.PENDING_CONCURRENCY_ACQUIRES).forEach(pending::record);
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attributes) {
        if (S3ConnectionPoolTimeout.isCause(context.exception())) {
            acquireTimeout.increment();
        }
    }

    @Override
    public void close() {
        // 미터는 레지스트리 소유 — 닫을 것 없음
    }
}
