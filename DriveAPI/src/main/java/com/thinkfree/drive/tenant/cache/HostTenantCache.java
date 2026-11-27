package com.thinkfree.drive.tenant.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thinkfree.drive.global.cache.CacheInvalidationBroadcaster;
import com.thinkfree.drive.global.cache.LocallyInvalidatable;
import com.thinkfree.drive.global.cache.NoopCacheInvalidationBroadcaster;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * host → tenantSeq 매핑 캐시. Caffeine 기반 (TTL + LRU eviction 자동).
 * <p>
 * Filter 외부에서도 invalidate 할 수 있도록 빈으로 분리. Tenant CRUD 시점에
 * {@link #invalidateAll()} 호출하여 일관성 유지.
 * <p>
 * 트랜잭션 안에서 부르면 커밋(또는 롤백) 뒤 한 번 더 비운다 — 즉시 무효화와 커밋 사이에 들어온 요청이
 * 아직 커밋 안 된 DB 를 읽고 이전 매핑을 다시 캐시하면 TTL 동안 남기 때문(작업 E14).
 * <p>
 * 다른 WAS 에는 {@link CacheInvalidationBroadcaster} 로 알린다(Redis 를 켠 설치만, 커밋 뒤). 못 받은 WAS 는 TTL 안에 반영.
 */
@Component
public class HostTenantCache implements LocallyInvalidatable {

    public static final String CACHE_NAME = "host-tenant";

    private final Cache<String, Integer> cache;
    private final CacheInvalidationBroadcaster broadcaster;

    /** 다른 WAS 에 알리지 않는 캐시(단위 테스트·단일 WAS) */
    public HostTenantCache(TenantProperty tenantProperty) {
        this(tenantProperty, NoopCacheInvalidationBroadcaster.INSTANCE);
    }

    @Autowired
    public HostTenantCache(TenantProperty tenantProperty, CacheInvalidationBroadcaster broadcaster) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(tenantProperty.cacheTtlSeconds()))
                .maximumSize(tenantProperty.cacheMaxSize())
                .build();
        this.broadcaster = broadcaster;
    }

    public Integer get(String host) {
        return cache.getIfPresent(host);
    }

    public void put(String host, Integer tenantSeq) {
        cache.put(host, tenantSeq);
    }

    public void invalidate(String host) {
        cache.invalidate(host);
        broadcaster.broadcast(CACHE_NAME, host);
    }

    public void invalidateAll() {
        cache.invalidateAll();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.invalidateAll();
                }
            });
        }
        broadcaster.broadcast(CACHE_NAME, CacheInvalidationBroadcaster.ALL);
    }

    @Override
    public String cacheName() {
        return CACHE_NAME;
    }

    @Override
    public void evictLocal(String key) {
        if (CacheInvalidationBroadcaster.ALL.equals(key)) {
            cache.invalidateAll();
        } else {
            cache.invalidate(key);
        }
    }
}
