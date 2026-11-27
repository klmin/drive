package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.global.exception.GlobalErrorCode;
import com.thinkfree.drive.util.RequestPathUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * 업로드 본문을 받는 요청의 WAS(JVM) 단위 동시 상한 — docs/design/scaling-strategy.md §3-3.
 *
 * <p>업로드 본문은 동기 요청이라 Tomcat 요청 스레드가 전송이 끝날 때까지(파트 16MB, 느린 회선이면 수십 초) S3 연결과 함께 붙잡힌다.
 * 상한이 없으면 업로드가 몰리거나 한 클라이언트가 파트를 한꺼번에 보낼 때 Tomcat 스레드(200)를 거의 다 써서 목록·로그인까지 막힌다.
 * <ul>
 *   <li>대상: 청크 파트 {@code PUT /api/v1/uploads/{id}/parts/{n}}(octet-stream), 단일 업로드 {@code POST /api/v1/files}(multipart).
 *       multipart 본문은 DispatcherServlet 이 컨트롤러·인터셉터보다 먼저 다 읽으므로 서블릿 필터여야 "본문 읽기 전 거절" 이 된다.</li>
 *   <li>{@link Semaphore#tryAcquire()} — 기다리지 않는다. 기다리는 동안에도 Tomcat 스레드를 잡기 때문. 넘치면 503 + {@code Retry-After},
 *       프론트(chunkUpload.js·UploadDialog)가 잠시 뒤 다시 보낸다.</li>
 *   <li>본문을 읽지 않고 답하므로 연결을 닫는다({@code Connection: close}) — 남은 본문이 다음 요청으로 읽히지 않게.</li>
 *   <li>그 WAS 의 스레드·S3 풀을 지키는 상한이라 WAS 마다 따로 센다. 전체 상한 = 상한 × WAS 대수.
 *       {@code storage.http.max-connections} ≥ 다운로드 상한 + 이 상한 + 20 으로 같이 정한다.</li>
 * </ul>
 * 지표: {@code drive_upload_body_active}(사용 중), {@code drive_upload_body_max}(상한), {@code drive_upload_body_rejected_total}(거절 수).
 */
@Slf4j
public class UploadConcurrencyLimitFilter extends OncePerRequestFilter {

    static final String ACTIVE_METRIC = "drive.upload.body.active";
    static final String MAX_METRIC = "drive.upload.body.max";
    static final String REJECTED_METRIC = "drive.upload.body.rejected";
    static final String RETRY_AFTER_SECONDS = "1";

    private static final Pattern PART_PATH = Pattern.compile("^/api/v1/uploads/[^/]+/parts/[^/]+/?$");
    private static final Pattern SINGLE_UPLOAD_PATH = Pattern.compile("^/api/v1/files/?$");

    private final int maxConcurrent;
    private final Semaphore permits;
    private final Counter rejected;
    private final ObjectMapper objectMapper;

    public UploadConcurrencyLimitFilter(int maxConcurrent, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("drive.upload.max-concurrent-bodies must be >= 1: " + maxConcurrent);
        }
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent);
        this.objectMapper = objectMapper;
        this.rejected = Counter.builder(REJECTED_METRIC)
                .description("업로드 본문 동시 상한을 넘어 본문을 읽기 전에 거절(503)한 요청 수")
                .register(meterRegistry);
        Gauge.builder(ACTIVE_METRIC, this, UploadConcurrencyLimitFilter::activeCount)
                .description("지금 본문을 받고 있는 업로드 요청 수(이 WAS)")
                .register(meterRegistry);
        Gauge.builder(MAX_METRIC, this, f -> f.maxConcurrent)
                .description("업로드 본문 동시 상한(이 WAS)")
                .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isUploadBody(request.getMethod(), RequestPathUtil.pathWithinApplication(request));
    }

    static boolean isUploadBody(String method, String path) {
        if (path == null) {
            return false;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return PART_PATH.matcher(path).matches();
        }
        if ("POST".equalsIgnoreCase(method)) {
            return SINGLE_UPLOAD_PATH.matcher(path).matches();
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            log.warn("Upload body rejected — all {} upload permits busy", maxConcurrent);
            response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
            response.setHeader("Connection", "close");
            FilterResponseWriter.write(response, GlobalErrorCode.SERVICE_UNAVAILABLE, objectMapper);
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }

    int activeCount() {
        return maxConcurrent - permits.availablePermits();
    }
}
