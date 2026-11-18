package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import com.thinkfree.drive.user.enums.RoleType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 툴 role 검사 — 선언 중 하나라도 보유하면 통과(authority 가 여러 개인 경우도 검사 — 지금 MCP 인증 경로는 역할 1개), 하이어라키 미적용.
 */
@DisplayName("McpToolRoleAspect — 보유 역할 중 하나라도 선언과 일치하면 통과")
class McpToolRoleAspectTest {

    private static final Object PROCEEDED = "proceeded";

    private final McpToolRoleAspect aspect = new McpToolRoleAspect();

    @McpToolRole(RoleType.ADMIN)
    static class AdminTools {
        @Tool(description = "admin")
        public Object adminTool() {
            return null;
        }
    }

    @McpToolRole(RoleType.USER)
    static class UserTools {
        @Tool(description = "user")
        public Object userTool() {
            return null;
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(RoleType acting, Set<RoleType> roles) {
        CustomUserDetails details = new CustomUserDetails(new UserDetailDto(10, "u@test.com", "u", acting.getAuthority(), AuthSource.EXTERNAL, 1, null, roles));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities()));
    }

    private static ProceedingJoinPoint joinPoint(Object target, String methodName) throws Throwable {
        Method method = target.getClass().getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        given(signature.getMethod()).willReturn(method);
        given(signature.getName()).willReturn(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        given(joinPoint.getSignature()).willReturn(signature);
        given(joinPoint.getTarget()).willReturn(target);
        given(joinPoint.proceed()).willReturn(PROCEEDED);
        return joinPoint;
    }

    @Test
    @DisplayName("ADMIN+USER 보유(OAuth) — 관리자 툴·사용자 툴 모두 통과")
    void multiRolePassesBoth() throws Throwable {
        authenticate(RoleType.ADMIN, Set.of(RoleType.ADMIN, RoleType.USER));

        assertThat(aspect.aroundTool(joinPoint(new AdminTools(), "adminTool"))).isEqualTo(PROCEEDED);
        assertThat(aspect.aroundTool(joinPoint(new UserTools(), "userTool"))).isEqualTo(PROCEEDED);
    }

    @Test
    @DisplayName("USER 키 — 관리자 툴 거부(실행 안 함), 사용자 툴 통과")
    void userKeyDeniedAdminTool() throws Throwable {
        authenticate(RoleType.USER, Set.of(RoleType.USER));
        ProceedingJoinPoint admin = joinPoint(new AdminTools(), "adminTool");

        assertThat(aspect.aroundTool(admin)).isNotEqualTo(PROCEEDED);
        verify(admin, never()).proceed();
        assertThat(aspect.aroundTool(joinPoint(new UserTools(), "userTool"))).isEqualTo(PROCEEDED);
    }

    @Test
    @DisplayName("SUPER_ADMIN — 하이어라키 미적용이라 관리자 툴 거부")
    void superAdminDeniedAdminTool() throws Throwable {
        authenticate(RoleType.SUPER_ADMIN, Set.of(RoleType.SUPER_ADMIN));
        ProceedingJoinPoint admin = joinPoint(new AdminTools(), "adminTool");

        assertThat(aspect.aroundTool(admin)).isNotEqualTo(PROCEEDED);
        verify(admin, never()).proceed();
    }
}
