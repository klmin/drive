package com.thinkfree.drive.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 무효화 구독 컨테이너를 뒤에서 시작한다 — Redis 가 없어도 앱 기동이 실패하지 않게.
 * 성공할 때까지 {@code retryInterval} 마다 다시 시도하고, 첫 실패만 WARN 으로 남긴다(나머지는 DEBUG — 로그가 넘치지 않게).
 */
@Slf4j
public class CacheInvalidationSubscriptionStarter implements SmartLifecycle {

    private final RedisMessageListenerContainer container;
    private final Duration retryInterval;
    private ScheduledExecutorService executor;

    private volatile boolean running;
    private volatile boolean warned;

    public CacheInvalidationSubscriptionStarter(RedisMessageListenerContainer container, Duration retryInterval) {
        this.container = container;
        this.retryInterval = retryInterval;
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-invalidation-subscribe");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tryStart, 0, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
        running = true;
    }

    void tryStart() {
        if (container.isRunning()) {
            executor.shutdown();
            return;
        }
        try {
            container.start();
            log.info("[CacheInvalidation] 무효화 채널 구독 시작");
            executor.shutdown();
        } catch (Exception e) {
            try {
                container.stop();
            } catch (Exception ignored) {
                // 반쯤 시작된 상태 정리 — 다음 시도에서 처음부터
            }
            if (!warned) {
                warned = true;
                log.warn("[CacheInvalidation] 무효화 채널 구독 실패 — {}초마다 다시 시도, 그동안 다른 WAS 의 변경은 TTL 로 반영: {}",
                        retryInterval.toSeconds(), e.getMessage());
            } else {
                log.debug("[CacheInvalidation] 구독 재시도 실패", e);
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
