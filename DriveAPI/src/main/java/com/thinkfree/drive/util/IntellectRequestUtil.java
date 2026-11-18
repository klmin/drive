package com.thinkfree.drive.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 인텔릭트 화면(/intellect)에서 온 요청인지 Referer 경로로 판별한다.
 * <p>인텔릭트는 mode 등 화면 정보를 API 요청에 싣지 않아 Referer 외 판별 수단이 없다.
 * Referrer-Policy가 경로를 제거하도록 바뀌면 항상 false가 된다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IntellectRequestUtil {

    private static final String INTELLECT_PATH = "/intellect";

    public static boolean isFromIntellect(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null || referer.isBlank()) {
            return false;
        }

        try {
            String path = new URI(referer).getPath();
            return path != null && (path.equals(INTELLECT_PATH) || path.startsWith(INTELLECT_PATH + "/"));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
