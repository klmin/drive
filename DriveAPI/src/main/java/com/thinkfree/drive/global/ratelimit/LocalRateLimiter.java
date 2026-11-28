package com.thinkfree.drive.global.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * WAS 메모리 버킷 — Redis 를 쓰지 않는 설치의 기본이자 Redis 장애 때의 대체.
 * 버킷은 마지막 사용 뒤 10분이면 버린다(그 사이 다 채워진다 — 규칙 기간이 10분보다 짧아야 한다). 최대 10만 개.
 */
@Component
public class LocalRateLimiter implements RateLimiter {

    static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);
    static final long MAX_BUCKETS = 100_000;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAX_BUCKETS)
            .build();

    @Override
    public Duration tryConsume(String key, RateLimitRule rule) {
        Bucket bucket = buckets.get(rule.name() + ":" + key,
                k -> Bucket.builder().addLimit(rule.toConfiguration().getBandwidths()[0]).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(Math.max(1, probe.getNanosToWaitForRefill()));
    }
}
