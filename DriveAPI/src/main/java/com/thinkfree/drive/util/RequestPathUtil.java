package com.thinkfree.drive.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 요청 경로 유틸.
 * <p>
 * 경로 리터럴("/api/v1/...", "/mcp") 비교에는 {@link HttpServletRequest#getRequestURI()} 대신 반드시
 * {@link #pathWithinApplication(HttpServletRequest)} 를 쓴다. PATH 테넌트 모드에서는 요청이
 * {@code /t/acme/api/v1/...} 로 들어오고 {@code /t/acme} 가 contextPath 로 에뮬레이션되므로, requestURI 는 테넌트
 * prefix 를 포함하지만 애플리케이션 내부 경로는 prefix 가 없다. (Spring Security 매처·MVC 매핑과 같은 기준)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestPathUtil {

    /**
     * contextPath 를 제외한 애플리케이션 내부 경로. servletPath + pathInfo 가 비어 있으면
     * (MockHttpServletRequest 등) requestURI 에서 contextPath 를 떼어 반환한다.
     */
    public static String pathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
        if (StringUtils.hasText(path)) {
            return path;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /** 경로의 첫 세그먼트("/acme/api/v1" → "acme"). 없으면 빈 문자열. */
    public static String firstSegment(String path) {
        return segment(path, 0);
    }

    /**
     * 경로의 {@code index} 번째(0 기준) 세그먼트("/t/acme/api" 의 1 → "acme"). 없으면 빈 문자열.
     * 빈 세그먼트("/t//x" 의 1)는 빈 문자열로 그대로 반환한다(건너뛰지 않음).
     */
    public static String segment(String path, int index) {
        if (!StringUtils.hasText(path) || index < 0) {
            return "";
        }
        int start = path.startsWith("/") ? 1 : 0;
        for (int i = 0; i < index; i++) {
            int slash = path.indexOf('/', start);
            if (slash < 0) {
                return "";
            }
            start = slash + 1;
        }
        int end = path.indexOf('/', start);
        return end < 0 ? path.substring(start) : path.substring(start, end);
    }
}
