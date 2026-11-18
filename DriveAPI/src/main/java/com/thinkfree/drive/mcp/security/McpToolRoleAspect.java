package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 툴 role 검증 어스펙트 — 각 툴이 {@link McpToolRole}로 선언한 사용 가능 role과
 * 현재 계정의 보유 role(필터가 실은 ROLE_* authority — API Key 는 키 역할, OAuth 는 토큰 role 클레임 역할, 둘 다 1개)을
 * 대조해, 선언 중 하나라도 보유하면 통과하고 아니면 실행 없이 인밴드(McpToolResult.error)로 거부한다.
 *
 * 선언 해석: 메서드 {@code @McpToolRole} > 클래스 {@code @McpToolRole} > 없으면 거부(default-deny).
 * role 비교는 정확 일치 — 전역 RoleHierarchy(SUPER_ADMIN > ADMIN)를 의도적으로
 * 적용하지 않는다. 하이어라키는 "상위 role은 하위 권한을 내포한다"는 포함 모델이라
 * ADMIN이 USER 선언 툴을 통과하게 되는데, MCP 툴의 role은 "이 툴은 이 계정 유형 전용"이라는
 * 배타 모델이기 때문(user 전용 툴은 admin을 거부해야 한다). admin이 user 툴도 쓰게 하려면
 * 하이어라키가 아니라 {@code @McpToolRole({USER, ADMIN})}로 명시 선언한다.
 * 체인 게이트(McpSecurityConfig)는 scope만 보므로 연결·툴 목록 조회는 role과 무관하게 되고,
 * 호출만 거부된다 — LLM이 에러 메시지를 읽고 계정 role 문제임을 사용자에게 전달할 수 있다
 * (McpScopeGuard의 scope 거부와 동일한 관례).
 *
 * @Order(0): 로케일 어스펙트(순서 미지정 = 최후순위)보다 바깥에서 실행 — 거부될 호출은
 * 로케일 확인(사용자 조회)까지 가지 않는다.
 */
@Slf4j
@Aspect
@Component
@Order(0)
public class McpToolRoleAspect {

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();

        McpToolRole declared = resolveDeclaration(joinPoint);
        if (declared == null) {
            // 선언 누락 = 구성 오류 — 조용한 권한 부여 대신 실행을 막고 로그로 드러낸다
            log.warn("{ McpToolRoleAspect } : @McpToolRole 선언 없는 툴 거부 - tool : {}", toolName);
            return McpToolResult.error("이 도구는 사용 가능한 계정 유형이 설정되지 않아 실행할 수 없습니다. 관리자에게 문의해 주세요.");
        }

        Set<RoleType> current = currentRoles();
        if (Arrays.stream(declared.value()).anyMatch(current::contains)) {
            return joinPoint.proceed();
        }

        log.info("{ McpToolRoleAspect } : role 거부 - tool : {}, required : {}, current : {}",
                toolName, Arrays.toString(declared.value()), current);
        return McpToolResult.error(deniedMessage(declared.value(), current));
    }

    /** 메서드 선언 우선, 없으면 클래스 선언 — 클래스 기본값 + 툴별 예외 구조 */
    private McpToolRole resolveDeclaration(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        McpToolRole onMethod = AnnotatedElementUtils.findMergedAnnotation(method, McpToolRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), McpToolRole.class);
    }

    /** 현재 인증의 보유 role 전부 — 없으면 빈 Set (시큐리티 체인이 인증을 보장하므로 방어적) */
    private Set<RoleType> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(RoleType::fromAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleType.class)));
    }

    /** 툴 error 필드에 실을 표준 거부 메시지 — LLM이 원인(계정 role)을 사용자에게 전달할 수 있게 요구/현재 role을 명시 */
    private String deniedMessage(RoleType[] required, Set<RoleType> current) {
        String requiredTitles = Arrays.stream(required)
                .map(RoleType::getTitle)
                .collect(Collectors.joining(", "));
        String currentTitle = current.isEmpty() ? "알 수 없음" : current.stream()
                .map(RoleType::getTitle)
                .collect(Collectors.joining(", "));
        return "이 MCP 도구는 '" + requiredTitles + "' 계정 전용입니다. 현재 연결된 계정의 역할은 '"
                + currentTitle + "'이므로 사용할 수 없습니다. 해당 계정으로 다시 연결해 주세요.";
    }
}
