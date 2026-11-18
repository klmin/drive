package com.thinkfree.drive.auditlog.lookup.tenant.impl;

import com.thinkfree.drive.auditlog.lookup.tenant.TenantNameResolver;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TenantFindService#findByTenantSeq(int)} 결과를 seq→이름으로 캐시하는 리졸버.
 * <p>
 * 감사 전송은 async(gov-audit executor)에서 일어나므로 이 조회는 요청 스레드에 부담을 주지 않는다.
 * 테넌트명은 거의 변하지 않아 프로세스 수명 동안 캐시한다(rename 시 재기동 전까지 옛 이름이 남을 수 있으나
 * 감사 표시용이라 허용). 조회 실패는 삼키고 null 반환 → scope.name만 생략되고 전송 자체는 진행된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedTenantNameResolver implements TenantNameResolver {

    private final TenantFindService tenantFindService;
    private final Map<Integer, String> cache = new ConcurrentHashMap<>();

    @Override
    public String resolve(Integer tenantSeq) {
        if (tenantSeq == null) {
            return null;
        }
        String cached = cache.get(tenantSeq);
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
