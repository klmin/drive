package com.thinkfree.drive.oauth2.service;

import com.thinkfree.drive.global.ratelimit.LocalRateLimiter;
import com.thinkfree.drive.global.ratelimit.RateLimiter;
import com.thinkfree.drive.oauth2.properties.OAuth2RegistrationProperty;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** 익명 DCR 등록 횟수 제한 — IP 별은 공용 RateLimiter 토큰 버킷, 전역은 DB 기준. */
@DisplayName("OAuth2ClientRegistrationRateLimiter — IP 별·전역 등록 제한")
class OAuth2ClientRegistrationRateLimiterTest {

    private final DSLContext dsl = mock(DSLContext.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private OAuth2ClientRegistrationRateLimiter limiter(int perIp, int global) {
        return limiter(perIp, global, new LocalRateLimiter());
    }

    private OAuth2ClientRegistrationRateLimiter limiter(int perIp, int global, RateLimiter rateLimiter) {
        return new OAuth2ClientRegistrationRateLimiter(dsl,
                new OAuth2RegistrationProperty(perIp, global, Duration.ofHours(1), Duration.ofDays(7)), rateLimiter, meters);
    }

    private void recentRegistrations(int count) {
        given(dsl.fetchCount(any(Table.class), any(Condition.class))).willReturn(count);
    }

    @Test
    @DisplayName("IP 별 상한까지 허용하고 넘으면 거부(다시 채워질 때까지 대기 시간), 다른 IP 는 따로 센다")
    void perIpLimit() {
        recentRegistrations(0);
        OAuth2ClientRegistrationRateLimiter limiter = limiter(3, 500);

        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        Duration wait = limiter.tryAcquire("10.0.0.1");
        assertThat(wait).as("1시간에 3개 → 토큰 하나 약 20분").isBetween(Duration.ofMinutes(19), Duration.ofMinutes(21));
        assertThat(limiter.tryAcquire("10.0.0.2")).isZero();
        assertThat(meters.counter("drive.rate.limit.rejected", "rule", "dcr-ip").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("전역 상한(window 안 DB 등록 수)에 닿으면 새 IP 도 거부하고 window 만큼 기다리라고 한다")
    void globalLimit() {
        OAuth2ClientRegistrationRateLimiter limiter = limiter(5, 500);
        recentRegistrations(500);

        assertThat(limiter.tryAcquire("10.0.0.9")).isEqualTo(Duration.ofHours(1));

        recentRegistrations(0);
        assertThat(limiter.tryAcquire("10.0.0.9")).isZero();
    }

    @Test
    @DisplayName("IP 를 알 수 없으면 한 버킷(unknown)으로 묶어 센다")
    void unknownIp() {
        recentRegistrations(0);
        OAuth2ClientRegistrationRateLimiter limiter = limiter(1, 500);

        assertThat(limiter.tryAcquire(null)).isZero();
        assertThat(limiter.tryAcquire(null)).isPositive();
    }

    @Test
    @DisplayName("WAS 가 버킷을 공유하면(Redis) 두 인스턴스 합쳐 상한만큼만 — 같은 RateLimiter 로 흉내")
    void sharedAcrossInstances() {
        recentRegistrations(0);
        RateLimiter shared = new LocalRateLimiter();
        OAuth2ClientRegistrationRateLimiter was1 = limiter(2, 500, shared);
        OAuth2ClientRegistrationRateLimiter was2 = limiter(2, 500, shared);

        assertThat(was1.tryAcquire("10.0.0.3")).isZero();
        assertThat(was2.tryAcquire("10.0.0.3")).isZero();
        assertThat(was1.tryAcquire("10.0.0.3")).isPositive();
        assertThat(was2.tryAcquire("10.0.0.3")).isPositive();
    }

    @Test
    @DisplayName("같은 IP 의 동시 요청도 상한만큼만 허용한다")
    void perIpLimitUnderConcurrency() throws Exception {
        recentRegistrations(0);
        OAuth2ClientRegistrationRateLimiter limiter = limiter(10, 500);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (limiter.tryAcquire("10.0.0.7").isZero()) {
                        allowed.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(allowed.get()).isEqualTo(10);
    }
}
