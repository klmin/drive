package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.global.FilterResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        
        Object deniedScope = request.getAttribute(ApiScopeAuthorizationManager.SCOPE_DENIED_ATTRIBUTE);

        log.info("{ ApiKeyAccessDeniedHandler } : Access denied for URI: {}, requiredScope: {}",
                request.getRequestURI(), deniedScope);

        // scope 미보유와 role 미허용을 클라이언트가 구분할 수 있어야 한다
        FilterResponseWriter.write(response,
                deniedScope != null ? ApiKeyErrorCode.API_KEY_SCOPE_INSUFFICIENT : AuthErrorCode.ACCESS_DENIED,
                objectMapper);
    }
}
