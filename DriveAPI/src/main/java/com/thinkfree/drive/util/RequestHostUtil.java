package com.thinkfree.drive.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 요청 host 추출 유틸. 프록시 뒤 배포를 고려해 {@code X-Forwarded-Host} 를 {@code Host} 보다 우선한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestHostUtil {

    /**
     * 포트를 제외한 소문자 host 이름. 다중 값("a.com, b.com")은 첫 값. 없으면 null.
     */
    public static String hostName(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Host");
        String host = StringUtils.hasText(forwarded) ? forwarded : request.getHeader(HttpHeaders.HOST);
        if (!StringUtils.hasText(host)) {
            return null;
        }
        int comma = host.indexOf(',');
        if (comma >= 0) {
            host = host.substring(0, comma);
        }
        host = host.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return StringUtils.hasText(host) ? host : null;
    }
}
