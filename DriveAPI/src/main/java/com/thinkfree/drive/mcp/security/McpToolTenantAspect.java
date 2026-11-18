package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.tenant.TenantBoundarySupport;
import com.thinkfree.drive.global.security.tenant.TenantResolver;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * MCP 툴 tenant 경계 검증 어스펙트 — 웹의 {@link com.thinkfree.drive.global.security.tenant.TenantBoundaryAspect}와
 * 동형. 웹 어스펙트는 포인트컷이 api.v1.admin 컨트롤러에 묶여 있어 MCP 툴에서는 발화하지 않으므로,
 * 툴 파라미터에 붙은 {@link McpToolTenantUser}·{@link McpToolTenantApiKey}를 여기서 동일 규칙으로 검증한다
 * (검증 로직·에러 코드는 {@link TenantBoundarySupport} 공유 — 타테넌트·미존재 모두 NOT_FOUND로 숨김).
 * 검증 대상 자원이 늘면 MCP 전용 어노테이션을 추가하고 resolvers에 한 항목만 등록한다.
 *
 * 웹과 달리 {@code @Before}가 아니라 {@code @Around}인 이유: MCP 툴의 예상된 실패는
 * 인밴드 {error, errorCode} 계약인데, 그 변환은 툴 본문 안의 McpToolResult.execute가 담당한다.
 * 본문 진입 전 어드바이스에서 예외를 던지면 그 계약을 우회해 프레임워크 isError로 새므로,
 * 검증 실패는 여기서 직접 인밴드로 변환한다. 본문(proceed)의 예외는 잡지 않고 그대로 둔다.
 *
 * @Order(2): role(@Order(0))·scope(@Order(1)) 안쪽 — 거부될 호출은 tenant 조회(DB)까지 가지 않는다.
 * 로케일 어스펙트(순서 미지정 = 최후순위)보다는 바깥이지만, 에러 메시지는 ErrorCode enum 고정 문자열이라
 * 로케일 미적용 상태로 변환돼도 기존 인밴드 에러와 동일하다.
 */
@Slf4j
@Aspect
@Component
@Order(2)
public class McpToolTenantAspect {

    private final Map<Class<? extends Annotation>, TenantResolver<?>> resolvers;

    public McpToolTenantAspect(UserFindService userFindService, ApiKeyFindService apiKeyFindService) {
        this.resolvers = Map.of(
                McpToolTenantUser.class,
                new TenantResolver<>(Integer.class, userFindService::findTenantSeqByUserSeq, UserErrorCode.USER_NOT_FOUND),
                McpToolTenantApiKey.class,
                new TenantResolver<>(Integer.class, apiKeyFindService::findTenantSeqByApiKeySeq, ApiKeyErrorCode.API_KEY_NOT_FOUND)
        );
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        String missingParam = findMissingGuardedParam(joinPoint);
        if (missingParam != null) {
            log.info("{ McpToolTenantAspect } : 필수 파라미터 누락 - tool : {}, param : {}",
                    joinPoint.getSignature().getName(), missingParam);
            return McpToolResult.error(missingParam + "는 필수입니다.");
        }
        try {
            TenantBoundarySupport.validate(joinPoint, resolvers);
        } catch (ApiRuntimeException e) {
            log.info("{ McpToolTenantAspect } : tenant 경계 거부 - tool : {}, errorCode : {}",
                    joinPoint.getSignature().getName(), e.getCode().getErrorCode());
            return McpToolResult.error(e);
        }
        return joinPoint.proceed();
    }

    /**
     * 검증 어노테이션이 붙었는데 값이 null인 첫 파라미터의 이름을 반환한다 (없으면 null).
     *
     * 웹 공용 {@link TenantBoundarySupport}는 null 파라미터를 건너뛴다 — 웹은 path variable이라 null이
     * 올 수 없다는 전제인데 MCP에선 성립하지 않는다(@ToolParam의 required는 스키마 선언일 뿐 런타임 강제가
     * 아니라 누락 시 null로 바인딩된 채 실행됨). 건너뛰면 어노테이션이 null 가드처럼 보여도 무동작이라
     * 본문에서 언박싱 NPE(isError로 샘)나 NOT_FOUND 오도가 나므로, 여기서 인밴드 필수 에러로 거부한다.
     * 메시지는 {@link com.thinkfree.drive.mcp.util.McpToolParams#require}와 동일 — 본문 require와
     * 중복돼도 무해하며, "어노테이션 선언만으로 가드된다"가 계약이라 후속 툴의 누락을 구조적으로 막는다.
     * 웹 공용 코드는 건드리지 않아 웹 경로 영향은 없다.
     */
    private String findMissingGuardedParam(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (args[i] != null) {
                continue;
            }
            for (Annotation annotation : parameters[i].getAnnotations()) {
                if (resolvers.containsKey(annotation.annotationType())) {
                    return parameters[i].getName();
                }
            }
        }
        return null;
    }
}
