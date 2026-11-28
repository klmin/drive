package com.thinkfree.drive.global.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Redis 를 쓰지 않는 설치 — 다른 WAS 에 알리지 않는다. 다른 WAS 는 각 캐시의 TTL 안에 반영된다. */
@Component
@ConditionalOnProperty(name = "drive.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoopCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

    public static final NoopCacheInvalidationBroadcaster INSTANCE = new NoopCacheInvalidationBroadcaster();

    @Override
    public void broadcast(String cacheName, String key) {
        // 로컬 캐시만 쓰는 구성 — 할 일 없음
    }
}
