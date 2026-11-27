package com.thinkfree.drive.global.ratelimit;

import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/**
 * 토큰 버킷 규칙 — 용량 {@code capacity}, {@code period} 동안 고르게 다시 채움. 순간 capacity 번, 평균 period 당 capacity 번.
 * @param name 키·지표에 쓰는 이름(':' 없이)
 */
public record RateLimitRule(String name, long capacity, Duration period) {

    public RateLimitRule {
        if (capacity < 1) {
            throw new IllegalArgumentException("rate limit capacity must be >= 1: " + name + "=" + capacity);
        }
    }

    BucketConfiguration toConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build();
    }
}
