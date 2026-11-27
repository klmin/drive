package com.thinkfree.drive.global.ratelimit;

import com.thinkfree.drive.global.redis.ConditionalOnDriveRedis;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * WAS 끼리 공유하는 버킷 — Bucket4j + Lettuce(CAS). 키 {@code <prefix>rl:<규칙>:<키>}, 버킷이 다 차는 시간 뒤 Redis 가 지운다.
 *
 * <p>Redis 연결은 처음 쓸 때 만든다 — Redis 가 없어도 기동은 된다. 연결·명령이 실패하면 {@link LocalRateLimiter} 로 판정한다
 * (그 순간 한도는 WAS 마다가 된다). 실패 뒤에는 {@link #RECONNECT_INTERVAL} 동안 Redis 를 건너뛴다 — 요청마다 연결 시도로 느려지지 않게.
 * Spring 의 Lettuce 클라이언트(연결 설정·TLS·비밀번호 그대로)로 연결을 하나 더 연다. 클러스터 구성은 지원하지 않아 메모리 버킷을 쓴다.
 */
@Slf4j
@Primary
@Component
@ConditionalOnDriveRedis
public class RedisRateLimiter implements RateLimiter {

    static final Duration REQUEST_TIMEOUT = Duration.ofMillis(500);
    static final Duration RECONNECT_INTERVAL = Duration.ofSeconds(5);

    private final Supplier<StatefulRedisConnection<String, byte[]>> connector;
    private final LocalRateLimiter fallback;
    private final String keyPrefix;
    private final Clock clock;
    private final Counter fallbacks;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;
    private volatile Instant skipRedisUntil = Instant.EPOCH;

    @Autowired
    public RedisRateLimiter(RedisConnectionFactory connectionFactory, LocalRateLimiter fallback,
                            DriveRedisProperty property, MeterRegistry meterRegistry) {
        this(() -> connect(connectionFactory), fallback, property, meterRegistry, Clock.systemUTC());
    }

    RedisRateLimiter(Supplier<StatefulRedisConnection<String, byte[]>> connector, LocalRateLimiter fallback,
                     DriveRedisProperty property, MeterRegistry meterRegistry, Clock clock) {
        this.connector = connector;
        this.fallback = fallback;
        this.keyPrefix = property.key("rl") + ":";
        this.clock = clock;
        this.fallbacks = Counter.builder("drive.rate.limit.redis.fallback")
                .description("Redis 버킷으로 판정하지 못해 WAS 메모리 버킷으로 판정한 횟수")
                .register(meterRegistry);
    }

    private static StatefulRedisConnection<String, byte[]> connect(RedisConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException("Lettuce 연결이 아니다: " + connectionFactory.getClass().getName());
        }
        AbstractRedisClient client = lettuce.getRequiredNativeClient();
        if (!(client instanceof RedisClient redisClient)) {
            throw new IllegalStateException("단일·Sentinel Redis 만 지원한다: " + client.getClass().getName());
        }
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Override
    public Duration tryConsume(String key, RateLimitRule rule) {
        if (clock.instant().isBefore(skipRedisUntil)) {
            fallbacks.increment();
            return fallback.tryConsume(key, rule);
        }
        try {
            ConsumptionProbe probe = proxyManager().builder()
                    .build(keyPrefix + rule.name() + ":" + key, rule::toConfiguration)
                    .tryConsumeAndReturnRemaining(1);
            return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(Math.max(1, probe.getNanosToWaitForRefill()));
        } catch (Exception e) {
            fallbacks.increment();
            skipRedisUntil = clock.instant().plus(RECONNECT_INTERVAL);
            resetConnection();
            log.warn("[RateLimit] Redis 버킷 실패 — {}초 동안 WAS 메모리 버킷으로 판정: {}", RECONNECT_INTERVAL.toSeconds(), e.toString());
            return fallback.tryConsume(key, rule);
        }
    }

    private ProxyManager<String> proxyManager() {
        ProxyManager<String> manager = proxyManager;
        if (manager != null) {
            return manager;
        }
        synchronized (this) {
            if (proxyManager == null) {
                connection = connector.get();
                proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .requestTimeout(REQUEST_TIMEOUT)
                        .build();
            }
            return proxyManager;
        }
    }

    private synchronized void resetConnection() {
        proxyManager = null;
        StatefulRedisConnection<String, byte[]> old = connection;
        connection = null;
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
                // 이미 끊겼다
            }
        }
    }

    @PreDestroy
    void close() {
        resetConnection();
    }
}
