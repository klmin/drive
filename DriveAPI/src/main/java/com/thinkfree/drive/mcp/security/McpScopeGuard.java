package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * MCP 툴 레벨 scope 검증 — 체인 게이트(McpSecurityConfig: mcp:* 하나라도)를 통과한
 * 요청에 대해, 개별 툴이 요구하는 세부 scope(read/write)를 확인한다.
 *
 * 호출 지점은 {@link McpToolScopeAspect} — 툴의 {@link McpToolScope} 선언을 읽어
 * hasScope로 검사하고, 부족하면 예외 대신 McpToolResult.error(deniedMessage(...))를
 * 반환한다(툴 에러 관례). 툴 코드가 직접 호출하지 않는다.
 * scope 값의 출처는 McpBearerTokenAuthenticationFilter가
 * 토큰에서 매핑한 SCOPE_ 프리픽스 authority다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpScopeGuard {

    public static boolean hasScope(OAuth2Scope scope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> scope.authority().equals(authority.getAuthority()));
    }

    /** 툴 error 필드에 실을 표준 거부 메시지 — 사용자가 재연동으로 해결할 수 있게 안내 */
    public static String deniedMessage(OAuth2Scope scope) {
        return "이 작업에는 " + scope.getValue() + " 권한이 필요하지만 현재 연결에는 승인되어 있지 않습니다. "
                + "Drive 연동을 해제 후 다시 연결하면서 해당 권한을 승인해 주세요.";
    }

    /** 현재 인증에 부여된 scope 목록 (SCOPE_ 프리픽스 제거) — 툴·리소스의 로깅용 */
    public static List<String> currentScopes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(OAuth2Scope.AUTHORITY_PREFIX))
                .map(authority -> authority.substring(OAuth2Scope.AUTHORITY_PREFIX.length()))
                .toList();
    }
}
