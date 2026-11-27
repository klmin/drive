package com.thinkfree.drive.accessrestriction.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thinkfree.drive.global.cache.CacheInvalidationBroadcaster;
import com.thinkfree.drive.global.cache.LocallyInvalidatable;
import com.thinkfree.drive.global.cache.NoopCacheInvalidationBroadcaster;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * IP·국가 접근 제한 정책 캐시 — docs/design/scaling-strategy.md §3-1.
 *
 * <p>{@code IpAccessFilter} 가 요청마다 허용 IP·허용 국가·사용 여부를 DB 에서 읽던 것을 (테넌트, 역할) 단위로 30초 보관한다.
 * <ul>
 *   <li>키에 테넌트를 반드시 넣는다 — 다른 테넌트의 정책으로 판정하면 접근 제한이 뚫리거나 엉뚱하게 막힌다.</li>
 *   <li>역할은 ADMIN(슈퍼관리자가 관리하는 관리자 허용 IP)과 USER(테넌트 관리자가 관리하는 사용자 허용 IP·국가) 두 갈래.</li>
 *   <li>정책을 바꾸는 쓰기(허용 IP·국가 교체, 사용 여부 변경, 테넌트 삭제)가 그 테넌트 항목을 비운다. 트랜잭션 안이면 커밋(롤백) 뒤
 *       한 번 더 비운다 — 커밋 전 사이에 들어온 요청이 이전 값을 다시 넣으면 TTL 동안 남기 때문({@code HostTenantCache} 와 같은 이유).</li>
 *   <li>다른 WAS 에는 {@link CacheInvalidationBroadcaster} 로 알린다(Redis 를 켠 설치만, 커밋 뒤). Redis 가 없거나 메시지를 놓친 WAS 는 최대 TTL(30초) 늦게 반영.</li>
 * </ul>
 */
@Component
public class AccessPolicyCache implements LocallyInvalidatable {

    static final Duration TTL = Duration.ofSeconds(30);
    static final long MAX_SIZE = 10_000;

    /** tenantSeq 는 null 일 수 있어(테넌트 미해석) Caffeine 키(null 불가)로 record 를 쓴다 */
    record Key(Integer tenantSeq, RoleType role) {
    }

    /**
     * 판정에 필요한 값 묶음.
     * @param enabled USER 정책의 사용 여부(ADMIN 은 항상 true — 허용 IP 가 있으면 검사)
     */
    public record Policy(boolean enabled, List<String> ipCidrs, List<String> countryCodes) {
        public Policy {
            ipCidrs = List.copyOf(ipCidrs);
            countryCodes = List.copyOf(countryCodes);
        }
    }

    public static final String CACHE_NAME = "access-policy";

    private final Cache<Key, Policy> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();
    private final CacheInvalidationBroadcaster broadcaster;

    /** 다른 WAS 에 알리지 않는 캐시(단위 테스트·단일 WAS) */
    public AccessPolicyCache() {
        this(NoopCacheInvalidationBroadcaster.INSTANCE);
    }

    @Autowired
    public AccessPolicyCache(CacheInvalidationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** ADMIN 은 ADMIN 정책, 그 밖의 역할(USER·ANONYMOUS)은 USER 정책 */
    public Policy get(Integer tenantSeq, RoleType role, Supplier<Policy> loader) {
        return cache.get(new Key(tenantSeq, policyRole(role)), k -> loader.get());
    }

    /** 이 테넌트의 ADMIN·USER 정책을 비운다 */
    public void invalidate(Integer tenantSeq) {
        evict(tenantSeq);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    evict(tenantSeq);
                }
            });
        }
        broadcaster.broadcast(CACHE_NAME, String.valueOf(tenantSeq));
    }

    @Override
    public String cacheName() {
        return CACHE_NAME;
    }

    /** 다른 WAS 에서 온 무효화 — key 는 tenantSeq 문자열("null" 은 테넌트 미해석), 전체면 ALL */
    @Override
    public void evictLocal(String key) {
        if (CacheInvalidationBroadcaster.ALL.equals(key)) {
            cache.invalidateAll();
            return;
        }
        evict("null".equals(key) ? null : Integer.valueOf(key));
    }

    private void evict(Integer tenantSeq) {
        cache.invalidate(new Key(tenantSeq, RoleType.ADMIN));
        cache.invalidate(new Key(tenantSeq, RoleType.USER));
    }

    static RoleType policyRole(RoleType role) {
        return role == RoleType.ADMIN ? RoleType.ADMIN : RoleType.USER;
    }
}
