package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        
        String uri = request.getRequestURI();
        log.info("[JwtAccessDeniedHandler] Access denied for URI: {}", uri);

        // 익명 계정의 거부는 기존 AnonymousAccessFilter 응답(REQUEST_006) 그대로 — 프론트가 이 코드로 공유 오류 페이지로 보낸다
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details && details.isAnonymous()) {
            FilterResponseWriter.write(response, GlobalErrorCode.INVALID_USER, objectMapper);
            return;
        }

        FilterResponseWriter.write(response, AuthErrorCode.ACCESS_DENIED, objectMapper);
    }
}

