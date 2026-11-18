package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.global.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/** {@link TenantFeatureRequired} 판정 — 요청 테넌트가 없으면(테넌트 없는 경로) 통과 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFeatureAspect {

    private final TenantPolicy tenantPolicy;

    @Before("@annotation(tenantFeatureRequired)")
    public void before(TenantFeatureRequired tenantFeatureRequired) {
        tenantPolicy.require(TenantContext.get(), tenantFeatureRequired.value());
    }
}
