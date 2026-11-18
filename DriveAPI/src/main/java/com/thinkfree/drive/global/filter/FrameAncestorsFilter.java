package com.thinkfree.drive.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Drive 화면을 iframe 으로 넣을 수 있는 곳을 같은 origin 과 {@code embed.allowed-origins}(developers 등)로 제한한다.
 * 정적 화면은 시큐리티 체인 밖에서 서빙되므로 서블릿 필터로 모든 응답에 붙인다. 허용 목록이 비어 있으면 같은 origin 만 허용.
 * <p>
 * iframe 진입 화면은 부모 창이 보낸 postMessage 만 받는다({@code event.source === window.parent}) — 부모가 될 수 있는
 * origin 을 이 헤더가 제한하므로 프론트가 origin 목록을 따로 비교하지 않아도 된다.
 */
public class FrameAncestorsFilter extends OncePerRequestFilter {

    static final String HEADER = "Content-Security-Policy";

    private final String policy;

    public FrameAncestorsFilter(List<String> allowedOrigins) {
        this.policy = allowedOrigins.isEmpty()
                ? "frame-ancestors 'self'"
                : "frame-ancestors 'self' " + String.join(" ", allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.addHeader(HEADER, policy);
        filterChain.doFilter(request, response);
    }
}
