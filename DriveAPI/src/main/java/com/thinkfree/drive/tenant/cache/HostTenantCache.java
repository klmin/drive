package com.thinkfree.drive.tenant.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thinkfree.drive.tenant.properties.TenantProperty;
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
 */
@Component
public class HostTenantCache {

    private final Cache<String, Integer> cache;

    public HostTenantCache(TenantProperty tenantProperty) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(tenantProperty.cacheTtlSeconds()))
                .maximumSize(tenantProperty.cacheMaxSize())
                .build();
    }

    public Integer get(String host) {
        return cache.getIfPresent(host);
    }

    public void put(String host, Integer tenantSeq) {
        cache.put(host, tenantSeq);
    }

    public void invalidate(String host) {
        cache.invalidate(host);
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
    }
}
