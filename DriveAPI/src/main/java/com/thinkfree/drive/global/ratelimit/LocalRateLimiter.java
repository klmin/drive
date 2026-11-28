package com.thinkfree.drive.global.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * WAS 메모리 버킷 — Redis 를 쓰지 않는 설치의 기본이자 Redis 장애 때의 대체.
 * 버킷은 마지막 사용 뒤 {@code max(10분, 규칙 기간)} 이면 버린다 — 그 사이 다 채워지므로 버려도 한도가 풀리지 않는다.
 * (규칙 기간보다 먼저 버리면 꽉 찬 새 버킷이 생겨 한도가 늘어난다.) 최대 10만 개.
 */
@Component
public class LocalRateLimiter implements RateLimiter {

    static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);
    static final long MAX_BUCKETS = 100_000;

    private record Entry(Bucket bucket, long idleNanos) {
    }

    private final Cache<String, Entry> buckets = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, Entry>() {
                @Override
                public long expireAfterCreate(String key, Entry entry, long currentTime) {
                    return entry.idleNanos();
                }

                @Override
                public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
                    return entry.idleNanos();
                }

                @Override
                public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
                    return entry.idleNanos();
                }
            })
            .maximumSize(MAX_BUCKETS)
            .build();

    @Override
    public Duration tryConsume(String key, RateLimitRule rule) {
        Entry entry = buckets.get(rule.name() + ":" + key, k -> new Entry(
                Bucket.builder().addLimit(rule.toConfiguration().getBandwidths()[0]).build(),
                idleExpiry(rule).toNanos()));
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(Math.max(1, probe.getNanosToWaitForRefill()));
    }

    static Duration idleExpiry(RateLimitRule rule) {
        return rule.period().compareTo(IDLE_EXPIRY) > 0 ? rule.period() : IDLE_EXPIRY;
    }
}
