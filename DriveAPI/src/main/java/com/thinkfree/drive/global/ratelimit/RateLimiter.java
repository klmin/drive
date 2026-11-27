package com.thinkfree.drive.global.ratelimit;

import java.time.Duration;

/**
 * 요청 수 제한 — docs/design/scaling-strategy.md §3-4.
 * <ul>
 *   <li>Redis 꺼짐: {@link LocalRateLimiter} — WAS 마다 메모리 버킷(실제 한도 = 설정값 × WAS 대수)</li>
 *   <li>Redis 켜짐: {@link RedisRateLimiter} — WAS 끼리 버킷 공유. Redis 장애면 메모리 버킷으로 계속(막지도, 다 통과시키지도 않음)</li>
 * </ul>
 */
public interface RateLimiter {

    /**
     * 토큰 하나를 쓴다.
     * @param key 버킷 키 — 테넌트 등 구분자를 호출부가 넣는다
     * @return 통과면 {@link Duration#ZERO}, 아니면 다음 토큰까지 기다릴 시간(양수)
     */
    Duration tryConsume(String key, RateLimitRule rule);
}
