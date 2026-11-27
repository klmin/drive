package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UploadConcurrencyLimitFilter — 업로드 본문 동시 상한(즉시 503·본문 미독·반납·지표)")
class UploadConcurrencyLimitFilterTest {

    private static final String PART = "/api/v1/uploads/7f1c3c1e-0000-0000-0000-000000000001/parts/3";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("대상 경로: 파트 PUT·단일 업로드 POST 만 센다")
    void matchesOnlyUploadBodies() {
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("PUT", PART)).isTrue();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("POST", "/api/v1/files")).isTrue();

        assertThat(UploadConcurrencyLimitFilter.isUploadBody("GET", PART)).isFalse();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("POST", "/api/v1/uploads")).isFalse();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("POST", "/api/v1/uploads/abc/complete")).isFalse();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("GET", "/api/v1/files")).isFalse();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("POST", "/api/v1/files/10/copy")).isFalse();
        assertThat(UploadConcurrencyLimitFilter.isUploadBody("PUT", null)).isFalse();
    }

    @Test
    @DisplayName("상한이 차면 기다리지 않고 본문을 읽기 전에 503 + Retry-After + Connection: close, 거절 수를 센다")
    void rejectsImmediatelyWithoutReadingBody() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UploadConcurrencyLimitFilter filter = new UploadConcurrencyLimitFilter(1, objectMapper, registry);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> {
                filter.doFilter(partRequest(), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    await(release);
                });
                return null;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(filter.activeCount()).isEqualTo(1);
            assertThat(registry.get(UploadConcurrencyLimitFilter.ACTIVE_METRIC).gauge().value()).isEqualTo(1.0);

            AtomicBoolean bodyRead = new AtomicBoolean();
            MockHttpServletRequest tracking = new MockHttpServletRequest("PUT", PART) {
                @Override
                public jakarta.servlet.ServletInputStream getInputStream() {
                    bodyRead.set(true);
                    return super.getInputStream();
                }
            };
            tracking.setContent(new byte[16]);
            AtomicBoolean chainCalled = new AtomicBoolean();
            MockHttpServletResponse rejected = new MockHttpServletResponse();
            long start = System.nanoTime();
            filter.doFilter(tracking, rejected, (req, res) -> chainCalled.set(true));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(rejected.getStatus()).isEqualTo(503);
            assertThat(rejected.getHeader("Retry-After")).isEqualTo(UploadConcurrencyLimitFilter.RETRY_AFTER_SECONDS);
            assertThat(rejected.getHeader("Connection")).isEqualTo("close");
            assertThat(rejected.getContentAsString()).contains("RESPONSE_004");
            assertThat(chainCalled).isFalse();
            assertThat(bodyRead).isFalse();
            assertThat(elapsedMs).isLessThan(1000);
            assertThat(registry.get(UploadConcurrencyLimitFilter.REJECTED_METRIC).counter().count()).isEqualTo(1.0);

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            assertThat(filter.activeCount()).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리 중 예외(연결 끊김 등)가 나도 허가를 반납한다")
    void releasesPermitOnException() {
        UploadConcurrencyLimitFilter filter = new UploadConcurrencyLimitFilter(1, objectMapper, new SimpleMeterRegistry());
        FilterChain failing = (req, res) -> {
            throw new IOException("client aborted");
        };
        assertThatThrownBy(() -> filter.doFilter(partRequest(), new MockHttpServletResponse(), failing))
                .isInstanceOf(IOException.class);
        assertThat(filter.activeCount()).isZero();

        MockHttpServletResponse next = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        assertThatThrownBy(() -> {
            filter.doFilter(partRequest(), next, (req, res) -> {
                chainCalled.set(true);
                throw new ServletException("boom");
            });
        }).isInstanceOf(ServletException.class);
        assertThat(chainCalled).isTrue();
        assertThat(filter.activeCount()).isZero();
    }

    @Test
    @DisplayName("업로드가 아닌 요청은 상한과 상관없이 통과하고 세지 않는다")
    void otherRequestsBypass() throws Exception {
        UploadConcurrencyLimitFilter filter = new UploadConcurrencyLimitFilter(1, objectMapper, new SimpleMeterRegistry());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> {
                filter.doFilter(partRequest(), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    await(release);
                });
                return null;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicBoolean chainCalled = new AtomicBoolean();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/resources"), res, (rq, rs) -> chainCalled.set(true));
            assertThat(chainCalled).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(filter.activeCount()).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("상한은 1 이상이어야 한다")
    void rejectsInvalidLimit() {
        assertThatThrownBy(() -> new UploadConcurrencyLimitFilter(0, objectMapper, new SimpleMeterRegistry()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest partRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", PART);
        request.setContentType("application/octet-stream");
        return request;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
