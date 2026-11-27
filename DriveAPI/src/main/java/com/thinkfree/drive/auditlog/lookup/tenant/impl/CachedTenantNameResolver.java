package com.thinkfree.drive.auditlog.lookup.tenant.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.thinkfree.drive.auditlog.lookup.tenant.TenantNameResolver;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * {@link TenantFindService#findByTenantSeq(int)} 결과를 seq→이름으로 캐시하는 리졸버.
 * <p>
 * 감사 전송은 async(gov-audit executor)에서 일어나므로 이 조회는 요청 스레드에 부담을 주지 않는다.
 * 테넌트명은 거의 변하지 않아 {@link #TTL}(5분) 동안 캐시한다 — 이름을 바꾸면 그 뒤 최대 5분까지 감사 로그에 옛 이름이 남는다(표시용이라 허용).
 * 예전에는 프로세스 수명 동안 보관해 재기동 전까지 옛 이름이 남았고 크기 제한도 없었다.
 * 조회 실패·없는 테넌트는 캐시하지 않는다. 실패는 삼키고 null 반환 → scope.name만 생략되고 전송 자체는 진행된다.
 */
@Slf4j
@Service
public class CachedTenantNameResolver implements TenantNameResolver {

    static final Duration TTL = Duration.ofMinutes(5);
    static final long MAX_SIZE = 10_000;

    private final TenantFindService tenantFindService;
    private final Cache<Integer, String> cache;

    @Autowired
    public CachedTenantNameResolver(TenantFindService tenantFindService) {
        this(tenantFindService, Ticker.systemTicker());
    }

    /** 테스트가 시간을 넘길 수 있게 Ticker 를 받는다 */
    CachedTenantNameResolver(TenantFindService tenantFindService, Ticker ticker) {
        this.tenantFindService = tenantFindService;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .build();
    }

    @Override
    public String resolve(Integer tenantSeq) {
        if (tenantSeq == null) {
            return null;
        }
        String cached = cache.getIfPresent(tenantSeq);
        if (cached != null) {
            return cached;
        }
        try {
            TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
            String name = tenant != null ? tenant.getTenantName() : null;
            if (name != null) {
                cache.put(tenantSeq, name);
            }
            return name;
        } catch (Exception e) {
            log.warn("[AuditLog] tenant name resolve failed for tenantSeq={} - scope.name omitted", tenantSeq, e);
            return null;
        }
    }
}
