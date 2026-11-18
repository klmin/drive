package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.util.RequestHostUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

/**
 * 아웃바운드 origin 의 host 를 DB(테넌트 도메인/베이스 도메인)에서 못 구할 때의 공용 폴백 —
 * 현재 요청 host(X-Forwarded-Host 우선, 포트 제거) → {@code app.front.base-url} 의 host → null.
 * 신규 설치(테넌트 domain NULL, 베이스 도메인 미설정)에서 {@code https://null/...} 링크가 나가는 것을 막는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PublicHostFallback {

    static String requestHostOrConfigured(AppFrontProperty appFrontProperty) {
        HttpServletRequest request = currentRequestOrNull();
        String requestHost = request == null ? null : RequestHostUtil.hostName(request);
        if (requestHost != null) {
            return requestHost;
        }
        return frontBaseUrlHostOrNull(appFrontProperty);
    }

    private static String frontBaseUrlHostOrNull(AppFrontProperty appFrontProperty) {
        String baseUrl = appFrontProperty == null ? null : appFrontProperty.baseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequestOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }
}
