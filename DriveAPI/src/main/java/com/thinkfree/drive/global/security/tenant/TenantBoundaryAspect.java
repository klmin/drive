package com.thinkfree.drive.global.security.tenant;

import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.global.security.tenant.annotation.TenantApiKey;
import com.thinkfree.drive.global.security.tenant.annotation.TenantUser;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 어드민 path 컨트롤러 진입 시점에 자원별 tenant 경계 검증을 수행한다.
 *
 * <p>자원별 검증 방법({@link TenantResolver})을 어노테이션별로 Map에 등록(생성자에서 1회).
 * 실제 검증은 {@link TenantBoundarySupport}가 파라미터를 한 번만 순회하며 처리.
 *
 * <p>신규 자원 추가 시 {@link #resolvers} Map에 한 항목만 추가하면 됨.
 *
 * <ul>
 *   <li>{@link TenantUser} (Integer userSeq) → {@link UserFindService#findTenantSeqByUserSeq}, USER_NOT_FOUND</li>
 *   <li>{@link TenantApiKey} (Integer apiKeySeq) → {@link ApiKeyFindService#findTenantSeqByApiKeySeq}, API_KEY_NOT_FOUND</li>
 * </ul>
 *
 * <p>다건(List)은 별도 {@link TenantValidator} 사용.
 */
@Aspect
@Component
public class TenantBoundaryAspect {

    private final Map<Class<? extends Annotation>, TenantResolver<?>> resolvers;

    public TenantBoundaryAspect(UserFindService userFindService, ApiKeyFindService apiKeyFindService) {
        this.resolvers = Map.of(
                TenantUser.class,
                new TenantResolver<>(Integer.class, userFindService::findTenantSeqByUserSeq, UserErrorCode.USER_NOT_FOUND),
                TenantApiKey.class,
                new TenantResolver<>(Integer.class, apiKeyFindService::findTenantSeqByApiKeySeq, ApiKeyErrorCode.API_KEY_NOT_FOUND)
        );
    }

    @Before("execution(* com.thinkfree.drive.api.v1.admin..controller..*(..))")
    public void validate(JoinPoint joinPoint) {
        TenantBoundarySupport.validate(joinPoint, resolvers);
    }
}
