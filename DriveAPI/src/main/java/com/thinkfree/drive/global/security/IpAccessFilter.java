package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.access.dto.IpAccessCheckCommand;
import com.thinkfree.drive.access.service.IpAccessControlService;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.security.SuperAdminIpGuard;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.IpUtil;
import com.thinkfree.drive.util.RequestPathUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter 다음 위치 — IP 기반 접근 제어.
 *
 * 분기 정책:
 * 1. IP_WHITELIST (LOGOUT_ENDPOINTS: 로그아웃 / TENANT_PUBLIC: branding, login-config / HTTP_STORAGE_ENDPOINTS: 오피스 어댑터 콜백 / PROFILE_IMAGE_ENDPOINTS: 프로필 이미지) → 검증 면제
 * 2. role 결정:
 *    - 로그인 진입점(SecurityContext 비어있음) → path 매칭으로 강제 주입
 *    - 그 외 → SecurityContext role (API Key 인증은 제외 — 서버 간 호출이라 사용자 IP 정책 대상 아님)
 *    - 결정 실패 (인증 없음 + 로그인 path 아님) → 통과
 * 3. role 별 가드:
 *    - SUPER_ADMIN → SuperAdminIpGuard (property 기반)
 *    - ADMIN, USER → IpAccessControlService (tenant + role 기반 DB 정책)
 */
@Slf4j
@RequiredArgsConstructor
public class IpAccessFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SuperAdminIpGuard superAdminIpGuard;
    private final IpAccessControlService ipAccessControlService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.IP_WHITELIST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        
        // PATH 테넌트 모드에서 requestURI 는 /t/acme prefix 를 포함 — 로그인 경로 리터럴 매칭은 contextPath 제외 경로로
        String path = RequestPathUtil.pathWithinApplication(request);
        String clientIp = IpUtil.extractClientIp(request);
       
        RoleType role = resolveRole(path);
       
        if (role == null) {
            chain.doFilter(request, response);
            return;
        }

        // TenantContext 가 비어 있는 경우(TenantFilter 스킵 경로 등) — 테넌트 IP 정책을 적용할 테넌트가 없다.
        // 여기서 500 을 내지 않고 통과시켜 뒤의 role 인가(403)가 판정하게 한다.
        Integer tenantSeq = TenantContext.get();
        if (role != RoleType.SUPER_ADMIN && tenantSeq == null) {
            log.warn("[IpAccessFilter] no tenant in context for role {} path {} — skipping tenant IP policy", role, LogSafe.of(path));
            chain.doFilter(request, response);
            return;
        }

        try {
            switch (role) {
                case SUPER_ADMIN -> superAdminIpGuard.checkAllowed(clientIp);
                case ADMIN, USER -> ipAccessControlService.checkAllowed(new IpAccessCheckCommand(clientIp, role, tenantSeq));
                // 익명 유저는 테넌트 미소속이므로 TenantContext(요청 주소 기준) + USER 수준 정책으로 검사
                case ANONYMOUS -> ipAccessControlService.checkAllowed(new IpAccessCheckCommand(clientIp, RoleType.USER, tenantSeq));
            }
        } catch (ApiRuntimeException e) {
            log.error("[IpAccessFilter] blocked clientIp : {} path : {}", LogSafe.of(clientIp), LogSafe.of(path));
            FilterResponseWriter.write(response, e.getCode(), objectMapper);
            return;
        }

        chain.doFilter(request, response);
    }

    private RoleType resolveRole(String path) {
        if (PATH_MATCHER.match(SecurityPaths.SUPER_ADMIN_LOGIN, path)){
            return RoleType.SUPER_ADMIN;
        }
        if (PATH_MATCHER.match(SecurityPaths.ADMIN_LOGIN, path)){
            return RoleType.ADMIN;
        }
        if (PATH_MATCHER.match(SecurityPaths.USER_LOGIN, path)){
            return RoleType.USER;
        }
        return extractAuthenticatedRole();
    }

    private RoleType extractAuthenticatedRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        // API Key 인증은 IP 정책 미적용 — 쿠키 요청만 검사
        if (details.isExternalApi()) {
            return null;
        }
        
        return RoleType.fromAuthority(details.getRoleType());
    }

}
