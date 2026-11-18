package com.thinkfree.drive.global.security.tenant;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 자원별 tenant 경계 검증 aspect의 공통 로직.
 *
 * <p>메서드 파라미터를 <b>한 번만</b> 순회하면서, 각 파라미터에 붙은 어노테이션을 resolvers에서 찾아
 * 매칭되는 {@link TenantResolver}로 tenant를 검증한다. 자원 종류가 늘어도 순회 횟수는 1번으로 유지된다.
 *
 * <p>resolvers Map은 aspect 생성자에서 한 번만 구성해 재사용한다. 신규 자원 추가 시 Map에 한 항목만 추가:
 * <pre>{@code
 * Map.of(
 *     TenantUser.class,   new TenantResolver<>(Integer.class, userFindService::findTenantSeqByUserSeq, USER_NOT_FOUND),
 *     TenantApiKey.class, new TenantResolver<>(Integer.class, apiKeyFindService::findTenantSeqByApiKeySeq, API_KEY_NOT_FOUND),
 *     TenantResource.class, new TenantResolver<>(Long.class, resourceFindService::findTenantSeqByResourceSeq, RESOURCE_NOT_FOUND)
 * )
 * }</pre>
 */
public final class TenantBoundarySupport {

    private TenantBoundarySupport() {}

    /**
     * 메서드 파라미터를 순회하며, resolvers에 등록된 어노테이션이 붙은 식별자의 tenant를 검증한다.
     * 검증 실패(다른 테넌트) 시 해당 resolver의 에러 코드로 throw.
     *
     * @param joinPoint AOP join point (advice가 받은 그대로 전달)
     * @param resolvers 어노테이션 → 자원별 검증 방법 매핑
     */
    public static void validate(
            JoinPoint joinPoint,
            Map<Class<? extends Annotation>, TenantResolver<?>> resolvers) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        boolean tenantResolved = false;
        Integer currentTenantSeq = null;

        for (int i = 0; i < parameters.length; i++) {
            Object value = args[i];
            if (value == null) {
                continue;
            }
            for (Annotation annotation : parameters[i].getAnnotations()) {
                TenantResolver<?> resolver = resolvers.get(annotation.annotationType());
                if (resolver == null) {
                    continue;
                }

                if (!tenantResolved) {
                    currentTenantSeq = TenantContext.getOrThrow();
                    tenantResolved = true;
                }

                Integer resourceTenantSeq = resolver.resolveTenantSeq(value);
                if (!currentTenantSeq.equals(resourceTenantSeq)) {
                    throw ApiRuntimeException.type(resolver.errorOnMismatch());
                }
            }
        }
    }
}
