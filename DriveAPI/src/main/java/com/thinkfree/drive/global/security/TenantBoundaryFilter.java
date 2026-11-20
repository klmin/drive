package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.global.exception.GlobalErrorCode;
import com.thinkfree.drive.global.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IpAccessFilter 다음 위치 — 사용자 테넌트와 요청 호스트 테넌트의 일치 검증.
 *
 * shouldNotFilter (필터 자체 면제):
 * - {@link SecurityPaths#AUTH_WHITELIST_MATCHER}: 인증 면제 경로 (토큰 없거나 자체 토큰 인증)
 * - {@link SecurityPaths#TENANT_CONSISTENCY_SKIP}: 어노테이션 없이 비즈니스 내부에서
 *   cross-tenant 정상 접근을 검증하는 경로 (예: 공유 링크 접속)
 *
 * doFilterInternal:
 * 1. SecurityContext 비어있음 → 통과 (AuthorizationFilter가 401 처리)
 * 2. SUPER_ADMIN → 통과
 * 3. userTenantSeq == TenantContext tenantSeq → 통과
 * 4. 그 외 → 403 TENANT_MISMATCH
 */
@Slf4j
@RequiredArgsConstructor
public class TenantBoundaryFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.AUTH_WHITELIST_MATCHER.matches(request) || SecurityPaths.TENANT_CONSISTENCY_SKIP.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
     
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            chain.doFilter(request, response);
            return;
        }
        
        if (details.isSuperAdmin()) {
            chain.doFilter(request, response);
            return;
        }

        if (details.isAnonymous()) {
            chain.doFilter(request, response);
            return;
        }

        Integer userTenantSeq = details.getTenantSeq();
        Integer requestTenantSeq = TenantContext.get();
        
        if (userTenantSeq == null || !userTenantSeq.equals(requestTenantSeq)) {
            log.error("[TenantBoundaryFilter] mismatch userSeq : {} userTenant : {} requestTenant : {} path : {}",
                    details.getUserSeq(), userTenantSeq, requestTenantSeq, LogSafe.of(request.getRequestURI()));
            FilterResponseWriter.write(response, GlobalErrorCode.INVALID_USER, objectMapper);
            return;
        }

        chain.doFilter(request, response);
    }
    
}
