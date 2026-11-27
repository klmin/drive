package com.thinkfree.drive.global.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("무효화 구독 시작 — Redis 가 늦게 떠도 뒤에서 다시 시도")
class CacheInvalidationSubscriptionStarterTest {

    @Test
    @DisplayName("처음 두 번 실패해도 기동은 막지 않고, 세 번째에 구독이 시작되면 더 시도하지 않는다")
    void retriesUntilStarted() throws Exception {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(inv -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RedisConnectionFailureException("down");
            }
            return null;
        }).when(container).start();

        CacheInvalidationSubscriptionStarter starter = new CacheInvalidationSubscriptionStarter(container, Duration.ofMillis(50));
        starter.start(); // 예외 없이 바로 돌아온다

        verify(container, timeout(2_000).times(3)).start();
        verify(container, atLeast(2)).stop(); // 실패한 시도는 정리
        Thread.sleep(300);
        verify(container, times(3)).start(); // 성공 뒤에는 더 시도하지 않음
        starter.stop();
    }
}
