package com.thinkfree.drive.global.ratelimit;

import lombok.Getter;

import java.time.Duration;

/** 요청 수 제한 초과 — 429 + {@code Retry-After}(초, 올림) */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final String rule;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String rule, Duration wait) {
        super("rate limit exceeded: " + rule);
        this.rule = rule;
        this.retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
    }
}
