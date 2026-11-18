package com.thinkfree.drive.tenant.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 테넌트(TenantContext)에서 {@link #value()} 기능이 막혀 있으면 SYSTEM_MANAGED_RESTRICTED 로 거부한다.
 * UseCase(없으면 컨트롤러) 메서드에 붙여 내부 API·외부 API·MCP 가 같은 판정을 탄다. 판정은 {@link TenantFeatureAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantFeatureRequired {

    TenantFeature value();
}
