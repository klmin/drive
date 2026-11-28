package com.thinkfree.drive.global.cache;

import com.thinkfree.drive.global.redis.ConditionalOnDriveRedis;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.List;

/**
 * 무효화 채널 구독 — WAS 마다 연결 하나를 열어 두고 Redis 가 메시지를 밀어 준다(폴링 아님).
 *
 * <p>컨테이너를 기동 때 바로 시작하지 않는다({@code autoStartup=false}) — 처음 구독이 Redis 에 닿지 못하면 예외가 나서 앱 기동이 실패한다.
 * {@link CacheInvalidationSubscriptionStarter} 가 뒤에서 5초마다 시작을 시도하고, 한 번 붙은 뒤 끊기면 컨테이너가 5초마다 다시 구독한다.
 * 그동안 캐시는 TTL 로만 반영된다.
 */
@Configuration
@ConditionalOnDriveRedis
public class RedisCacheInvalidationConfig {

    static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);

    @Bean
    RedisCacheInvalidationListener redisCacheInvalidationListener(List<LocallyInvalidatable> caches,
                                                                  RedisCacheInvalidationBroadcaster broadcaster) {
        return new RedisCacheInvalidationListener(caches, broadcaster.origin());
    }

    @Bean
    RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     RedisCacheInvalidationListener listener,
                                                                     DriveRedisProperty property) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public boolean isAutoStartup() {
                return false; // 시작은 CacheInvalidationSubscriptionStarter 가 뒤에서
            }
        };
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryBackoff(new FixedBackOff(RETRY_INTERVAL.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        container.addMessageListener(listener, new ChannelTopic(RedisCacheInvalidationBroadcaster.channel(property)));
        return container;
    }

    @Bean
    CacheInvalidationSubscriptionStarter cacheInvalidationSubscriptionStarter(RedisMessageListenerContainer cacheInvalidationListenerContainer) {
        return new CacheInvalidationSubscriptionStarter(cacheInvalidationListenerContainer, RETRY_INTERVAL);
    }
}
