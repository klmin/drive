package com.thinkfree.drive.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.mcp.util.McpUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * /mcp 체인 401 응답 처리 — 실패한 자격의 종류에 따라 응답을 가른다.
 *
 * <ul>
 *   <li><b>JWT·토큰 미제출</b> — MCP 스펙상 Resource Server는 인증 실패 시 401과 함께
 *       WWW-Authenticate 헤더에 protected resource 메타데이터 URL(RFC 9728)을 실어야
 *       한다. MCP 클라이언트(Claude 등)는 이 헤더를 보고 OAuth 플로우(디스커버리 →
 *       로그인)를 자동 시작한다 — 만료된 토큰은 재로그인으로 해결되는 사안이라 이게 정답.</li>
 *   <li><b>API Key(비 JWT 불투명 문자열)</b> — 키 만료·무효는 재발급 사안이라 OAuth를
 *       유도하면 안 된다(재인증에 성공하면 연결이 조용히 OAuth로 바뀌어 키 만료가 가려진다).
 *       resource_metadata 없이 RFC 6750 표준 error 파라미터와 전용 에러코드(API_KEY_004)로
 *       응답해 원인이 로그에서 바로 보이게 한다. 단 클라이언트가 well-known 기본 경로를
 *       직접 프로브하는 폴백(스펙 허용)까지는 서버가 막을 수 없다.</li>
 * </ul>
 *
 * 실패 종류는 McpBearerTokenAuthenticationFilter가 request attribute로 전달한다 —
 * 필터가 예외를 삼키고 체인을 계속 진행하는 설계라, commence()가 받는 예외는
 * ExceptionTranslationFilter가 만든 것이어서 원인 판별에 쓸 수 없다.
 *
 * 메타데이터 엔드포인트 자체는 McpProtectedResourceMetadataController가 노출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        
        if (Boolean.TRUE.equals(request.getAttribute(McpConstants.AUTH_FAILURE_API_KEY_ATTR))) {
            log.info("{ McpAuthenticationEntryPoint } : Unauthorized MCP access (API Key) : {}",
                    authException.getMessage());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"invalid_token\", error_description=\"invalid or expired API key\"");
            FilterResponseWriter.write(response, ApiKeyErrorCode.API_KEY_INVALID, objectMapper);
            response.getWriter().flush();
            return;
        }

        String resourceMetadataUrl = McpUrls.protectedResourceMetadataUrl(request);
        log.info("{ McpAuthenticationEntryPoint } : Unauthorized MCP access : {} (resource_metadata : {})",
                LogSafe.of(authException.getMessage()), LogSafe.of(resourceMetadataUrl));

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer resource_metadata=\"" + resourceMetadataUrl + "\"");
        FilterResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN, objectMapper);
        // McpRequestLoggingFilter의 tee 래퍼가 만든 PrintWriter는 write()만으로는
        // flush되지 않아 바디가 유실된다 — 명시적으로 흘려보낸다
        response.getWriter().flush();
    }
}
