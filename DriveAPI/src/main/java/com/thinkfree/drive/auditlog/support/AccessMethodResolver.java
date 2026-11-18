package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.util.RequestPathUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessMethodResolver {

    // override가 있으면 그대로 사용(예: ACCOUNT_LOCKED_STATUS_CHANGE 같은 SYSTEM 판단 이벤트).
    // 없으면 요청 경로로 판정: McpConstants.MCP_ENDPOINT(/mcp) -> MCP, DriveConstants.API_PUBLIC_V1_PREFIX(/api/public/v1) -> API,
    // 내부 경로라도 API Key 로 인증된 요청 -> API, 그 외 -> WEB.
    // 경로 접두는 MCP 서버 등록·외부 API 핸들러 매핑과 같은 상수를 써서 한 곳에서 바뀌게 한다.
    // HTTP 요청 컨텍스트 자체가 없으면(스케줄러 등) SYSTEM.
    public static AccessMethod resolve(AccessMethod override) {
        if (override != null) {
            return override;
        }

        HttpServletRequest request = currentRequestOrNull();
        if (request == null) {
            return AccessMethod.SYSTEM;
        }

        // PATH 테넌트 모드의 /acme prefix 를 제외한 애플리케이션 내부 경로로 판정
        String uri = RequestPathUtil.pathWithinApplication(request);
        if (uri.startsWith(McpConstants.MCP_ENDPOINT)) {
            return AccessMethod.MCP;
        }
        if (uri.startsWith(DriveConstants.API_PUBLIC_V1_PREFIX)) {
            return AccessMethod.API;
        }
        if (isApiKeyAuthenticated()) {
            return AccessMethod.API;
        }
        return AccessMethod.WEB;
    }

    /** API Key 인증 요청 — ApiKeyAuthenticationFilter 가 AuthSource.EXTERNAL 로 인증한다 */
    private static boolean isApiKeyAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails details
                && details.isExternalApi();
    }

    private static HttpServletRequest currentRequestOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }
}
