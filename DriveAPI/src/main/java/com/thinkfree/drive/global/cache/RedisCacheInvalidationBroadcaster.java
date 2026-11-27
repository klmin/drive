package com.thinkfree.drive.global.cache;

import com.thinkfree.drive.global.redis.ConditionalOnDriveRedis;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Redis pub/sub 으로 무효화를 알린다 — 채널 하나({@code <prefix>cache-invalidate}), payload {@code 보낸WAS:캐시이름:키}.
 *
 * <p>pub/sub 은 저장하지 않는다 — 그 순간 끊겨 있던 WAS 는 메시지를 놓치고, 각 캐시의 TTL 이 안전망이다.
 * 메시지는 관리자가 설정을 바꿀 때만 나가 양이 매우 적다.
 */
@Slf4j
@Component
@ConditionalOnDriveRedis
public class RedisCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

    static final String CHANNEL = "cache-invalidate";
    /** 이 WAS(애플리케이션 컨텍스트)의 표식 — 자기가 보낸 메시지는 구독자가 건너뛴다(이미 지웠다) */
    private final String origin = UUID.randomUUID().toString();

    private final StringRedisTemplate redis;
    private final String channel;
    private final Counter failures;

    public RedisCacheInvalidationBroadcaster(StringRedisTemplate redis, DriveRedisProperty property, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.channel = channel(property);
        this.failures = Counter.builder("drive.cache.invalidation.publish.failures")
                .description("다른 WAS 에 캐시 무효화를 알리지 못한 횟수 — 그 WAS 들은 TTL 안에 반영된다")
                .register(meterRegistry);
    }

    static String channel(DriveRedisProperty property) {
        return property.key(CHANNEL);
    }

    String origin() {
        return origin;
    }

    String payload(String cacheName, String key) {
        return origin + ":" + cacheName + ":" + key;
    }

    @Override
    public void broadcast(String cacheName, String key) {
        String message = payload(cacheName, key);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(message);
                }
            });
            return;
        }
        publish(message);
    }

    private void publish(String message) {
        try {
            redis.convertAndSend(channel, message);
        } catch (Exception e) {
            failures.increment();
            log.warn("[CacheInvalidation] PUBLISH 실패 — 다른 WAS 는 TTL 안에 반영된다: {}", message, e);
        }
    }
}
