package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.utils.AuthorizationExtractor;
import com.thinkfree.drive.global.FilterResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Serializable;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {
    
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean bearer = AuthorizationExtractor.extractToken(request).isPresent();
        String credential = bearer ? "API_KEY"
                : StringUtils.hasText(request.getHeader(AuthCookieManager.ROLE_HEADER)) ? "COOKIE" : "NONE";
        String role = AuthCookieManager.resolveCookieRole(request).map(Enum::name).orElse("-");
        log.info("{ JwtAuthenticationEntryPoint } : Unauthorized access - uri : {}, credential : {}, role : {}, reason : {}",
                request.getRequestURI(), credential, role, authException.getMessage());

        // Authorization Bearer 가 있으면 API Key 인증 실패, 없으면 쿠키 토큰 실패(만료·무효·사용자 없음)
        if (bearer) {
            FilterResponseWriter.write(response, ApiKeyErrorCode.API_KEY_INVALID, objectMapper);
            return;
        }

        FilterResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN, objectMapper);
    }
}
