package com.thinkfree.drive.global.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * PATH 테넌트 모드에서 {@code /t/acme} 를 서블릿 contextPath 처럼 보이게 하는 래퍼.
 * (Spring {@code ForwardedHeaderFilter} 가 X-Forwarded-Prefix 를 처리하는 기법과 동일)
 * <ul>
 *     <li>{@link #getContextPath()} — 원본 contextPath + {@code /t/acme}</li>
 *     <li>{@link #getServletPath()} — 원본에서 {@code /t/acme} 제거</li>
 *     <li>{@link #getRequestURI()} / {@link #getRequestURL()} — 원본 유지
 *         (SAS issuer 해석·Spring {@code RequestPath} 파싱은 requestURI − contextPath 로 계산)</li>
 * </ul>
 * 결과적으로 Spring Security 매처, MVC 핸들러 매핑, 정적 리소스, context-relative 리다이렉트
 * ({@code LoginUrlAuthenticationEntryPoint}, {@code redirect:} 뷰), Spring Session 쿠키 Path 가
 * 전부 테넌트 prefix 를 자동 반영한다.
 */
public class TenantPathRequestWrapper extends HttpServletRequestWrapper {

    private final String tenantPrefix;

    /**
     * @param prefix 테넌트 경로 접두 전체({@code /t/acme}, {@code TenantPathMarker.prefixOf(slug)}). 끝 슬래시 없음.
     */
    public TenantPathRequestWrapper(HttpServletRequest request, String prefix) {
        super(request);
        this.tenantPrefix = prefix;
    }

    /** 테넌트 경로 prefix ({@code /t/acme}). */
    public String tenantPrefix() {
        return tenantPrefix;
    }

    @Override
    public String getContextPath() {
        String original = super.getContextPath();
        return (original == null ? "" : original) + tenantPrefix;
    }

    @Override
    public String getServletPath() {
        return stripPrefix(super.getServletPath());
    }

    private String stripPrefix(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals(tenantPrefix)) {
            return "";
        }
        if (path.startsWith(tenantPrefix + "/")) {
            return path.substring(tenantPrefix.length());
        }
        return path;
    }
}
