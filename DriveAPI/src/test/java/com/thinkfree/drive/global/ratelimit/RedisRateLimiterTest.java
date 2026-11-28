package com.thinkfree.drive.global.ratelimit;

import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis 버킷 공유 — WAS 2대(리미터 두 개)가 한도를 합산으로 지키는지, Redis 장애면 메모리 버킷으로 계속하는지. Testcontainers Redis */
@DisplayName("요청 수 제한 — Redis 버킷")
class RedisRateLimiterTest {

    static GenericContainer<?> redis;
    static LettuceConnectionFactory factory;
    final DriveRedisProperty property = new DriveRedisProperty(true, "rlt:");

    @BeforeAll
    static void start() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
        redis.start();
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
    }

    @AfterAll
    static void stop() {
        if (factory != null) {
            factory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    private RedisRateLimiter was(LettuceConnectionFactory connectionFactory) {
        return new RedisRateLimiter(connectionFactory, new LocalRateLimiter(), property, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("WAS 2대 — 한도 5 를 두 대 합쳐 5번만 통과")
    void sharedAcrossWas() {
        RedisRateLimiter a = was(factory);
        RedisRateLimiter b = was(factory);
        RateLimitRule rule = new RateLimitRule("login-account", 5, Duration.ofMinutes(1));
        String key = "1:" + UUID.randomUUID();

        int passed = 0;
        for (int i = 0; i < 10; i++) {
            RedisRateLimiter was = i % 2 == 0 ? a : b;
            if (was.tryConsume(key, rule).isZero()) {
                passed++;
            }
        }
        assertThat(passed).isEqualTo(5);
        assertThat(a.tryConsume(key, rule)).as("기다릴 시간").isPositive();
        a.close();
        b.close();
    }

    @Test
    @DisplayName("Redis 에 닿지 않으면 메모리 버킷으로 판정 — 예외 없음, 메모리 한도는 지킨다")
    void fallsBackToLocalWhenRedisDown() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        dead.start();
        try {
            RedisRateLimiter limiter = was(dead);
            RateLimitRule rule = new RateLimitRule("login-account", 3, Duration.ofMinutes(1));
            AtomicInteger passed = new AtomicInteger();
            for (int i = 0; i < 5; i++) {
                if (limiter.tryConsume("k", rule).isZero()) {
                    passed.incrementAndGet();
                }
            }
            assertThat(passed).hasValue(3);
            limiter.close();
        } finally {
            dead.destroy();
        }
    }
}
