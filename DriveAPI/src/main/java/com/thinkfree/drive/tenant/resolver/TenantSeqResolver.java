package com.thinkfree.drive.tenant.resolver;

import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 판별 키(host / slug) → 테넌트 조회 + fallback. 주소 전략({@code TenantAddressingStrategy})과
 * {@code TenantFilter} 가 공유한다. 조회 결과(ACTIVE seq / INACTIVE / NOT_FOUND)는 모두 {@link HostTenantCache} 에
 * 캐시되며 테넌트 CRUD 시 {@link HostTenantCache#invalidateAll()} 로 비운다.
 * <ul>
 *     <li>{@link #lookupByHost} — SUBDOMAIN 모드: Host 전체 = {@code TB_TENANT.DOMAIN}</li>
 *     <li>{@link #lookupBySubdomain} — PATH 모드: slug = {@code TB_TENANT.SUBDOMAIN} 직접 조회(베이스 도메인·DOMAIN 컬럼 무관)</li>
 *     <li>{@link #resolveFallback} — 특정되는 테넌트가 없을 때 공용: 활성 SELF_MANAGED 테넌트가 정확히 1개면 그 seq,
 *         아니면 dev fallback({@code tenant.dev-fallback-enabled}), 아니면 null. 고정 키로 캐시.</li>
 * </ul>
 * INACTIVE 는 fallback 대상이 아니다 — 주소로 테넌트가 특정됐는데 비활성이면 필터가 그대로 404 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSeqResolver {

    public enum LookupStatus { ACTIVE, INACTIVE, NOT_FOUND }

    /** 조회 결과. {@code tenantSeq} 는 ACTIVE 일 때만 non-null. */
    public record Lookup(LookupStatus status, Integer tenantSeq) {
        static final Lookup INACTIVE = new Lookup(LookupStatus.INACTIVE, null);
        static final Lookup NOT_FOUND = new Lookup(LookupStatus.NOT_FOUND, null);

        public boolean isActive() {
            return status == LookupStatus.ACTIVE;
        }
    }

    /** 캐시 센티널 — 실제 tenantSeq 와 겹치지 않는 값 */
    private static final int CACHED_NOT_FOUND = Integer.MIN_VALUE;
    private static final int CACHED_INACTIVE = Integer.MIN_VALUE + 1;
    private static final String SUBDOMAIN_KEY_PREFIX = "path:";
    private static final String FALLBACK_KEY = "\0fallback";

    private final TenantFindService tenantFindService;
    private final TenantProperty tenantProperty;
    private final HostTenantCache hostTenantCache;

    /** SUBDOMAIN 모드 — Host 전체 = {@code TB_TENANT.DOMAIN} 으로 조회. */
    public Lookup lookupByHost(String host) {
        return lookupCached(host, () -> tenantFindService.findByDomain(host));
    }

    /** PATH 모드 — 경로 첫 세그먼트(slug) = {@code TB_TENANT.SUBDOMAIN} 으로 조회. 호스트·베이스 도메인은 보지 않는다. */
    public Lookup lookupBySubdomain(String subdomain) {
        return lookupCached(SUBDOMAIN_KEY_PREFIX + subdomain, () -> tenantFindService.findBySubdomain(subdomain));
    }

    /** 특정되는 테넌트가 없을 때 공용 fallback — 단일테넌트 → dev fallback. 결과는 고정 키로 캐시. */
    public Integer resolveFallback() {
        Integer cached = hostTenantCache.get(FALLBACK_KEY);
        if (cached != null) {
            return cached;
        }

        Integer single = tenantFindService.findSingleActiveSelfManagedTenantSeq();
        if (single != null) {
            log.info("[TenantSeqResolver] single-tenant fallback applied -> tenantSeq : {}", single);
            hostTenantCache.put(FALLBACK_KEY, single);
            return single;
        }

        if (tenantProperty.devFallbackEnabled()) {
            log.warn("[TenantSeqResolver] dev fallback applied -> tenantSeq : {}", tenantProperty.devDefaultTenantSeq());
            return tenantProperty.devDefaultTenantSeq();
        }
        return null;
    }

    /** 캐시 미스일 때만 {@code finder} 로 DB 조회, 세 결과(ACTIVE/INACTIVE/NOT_FOUND) 모두 캐시. */
    private Lookup lookupCached(String cacheKey, Supplier<TenantData> finder) {
        Integer cached = hostTenantCache.get(cacheKey);
        if (cached != null) {
            return fromCache(cached);
        }

        Lookup lookup = toLookup(finder.get());
        hostTenantCache.put(cacheKey, toCache(lookup));
        return lookup;
    }

    private Lookup toLookup(TenantData tenantData) {
        if (tenantData == null) {
            return Lookup.NOT_FOUND;
        }
        return TenantStatus.ACTIVE == tenantData.getStatus()
                ? new Lookup(LookupStatus.ACTIVE, tenantData.getTenantSeq())
                : Lookup.INACTIVE;
    }

    private Lookup fromCache(int cached) {
        if (cached == CACHED_NOT_FOUND) {
            return Lookup.NOT_FOUND;
        }
        if (cached == CACHED_INACTIVE) {
            return Lookup.INACTIVE;
        }
        return new Lookup(LookupStatus.ACTIVE, cached);
    }

    private int toCache(Lookup lookup) {
        return switch (lookup.status()) {
            case ACTIVE -> lookup.tenantSeq();
            case INACTIVE -> CACHED_INACTIVE;
            case NOT_FOUND -> CACHED_NOT_FOUND;
        };
    }
}
