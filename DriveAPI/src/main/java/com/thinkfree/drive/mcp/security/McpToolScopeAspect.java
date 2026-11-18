package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.mcp.support.McpToolResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * MCP 툴 scope 검증 어스펙트 — 툴이 {@link McpToolScope}로 선언한 요구 scope를
 * 현재 인증의 SCOPE_ authority와 대조해, 부족하면 실행 없이 인밴드(McpToolResult.error)로
 * 거부한다. 검사·메시지 로직은 {@link McpScopeGuard}를 재사용한다.
 *
 * 선언 해석: 메서드 {@code @McpToolScope} > 클래스 {@code @McpToolScope} > 없으면 통과 —
 * 선언 없는 툴은 읽기 툴이며, 읽기 요구조건은 체인 게이트(mcp:* 하나라도 = 쓰기⊇읽기 계약)가
 * 이미 보장하므로 default-deny가 아니라 통과가 맞다. role의 default-deny({@link McpToolRoleAspect})와
 * 기준이 다른 이유: role은 게이트가 안 보지만 scope 하한은 게이트가 본다.
 *
 * @Order(1): role 어스펙트(@Order(0)) 안쪽, 로케일 어스펙트(순서 미지정 = 최후순위) 바깥.
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class McpToolScopeAspect {

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        McpToolScope declared = resolveDeclaration(joinPoint);
        if (declared == null || McpScopeGuard.hasScope(declared.value())) {
            return joinPoint.proceed();
        }

        log.info("{ McpToolScopeAspect } : scope 거부 - tool : {}, required : {}, current : {}",
                joinPoint.getSignature().getName(), declared.value().getValue(), McpScopeGuard.currentScopes());
        return McpToolResult.error(McpScopeGuard.deniedMessage(declared.value()));
    }

    /** 메서드 선언 우선, 없으면 클래스 선언 — McpToolRoleAspect와 동일 규칙 */
    private McpToolScope resolveDeclaration(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        McpToolScope onMethod = AnnotatedElementUtils.findMergedAnnotation(method, McpToolScope.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), McpToolScope.class);
    }
}
