package com.thinkfree.drive.global.config;

import com.thinkfree.drive.tenant.addressing.TenantPathMarker;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 경로별 CORS 설정. CorsFilter 가 TenantFilter 보다 먼저 돌아 PATH 모드 접두({@code /t/{slug}})가 붙은 채로 매칭하므로 접두 판도 함께 등록한다.
 * <ul>
 *     <li>API Key·OAuth 토큰으로만 인증하는 경로(외부 API·MCP·OAuth 클라이언트용 엔드포인트) — 모든 origin, credentials 없음.
 *         고객사 웹에서 SDK 로 부르는 호출을 막지 않고, 쿠키는 싣지 않는다</li>
 *     <li>그 외(쿠키로 인증하는 내부 API 등) — {@code cors.allowed-origins} 에 등록한 origin 만, credentials 허용.
 *         비어 있으면 다른 origin 요청은 모두 거부(같은 origin 배포는 영향 없음)</li>
 * </ul>
 * 먼저 등록한 패턴이 우선한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorsConfigSourceFactory {

    /** 쿠키를 받지 않는 경로 — 접두 없는 판. 접두 판({@code /t/*}...)은 자동으로 함께 등록 */
    static final List<String> TOKEN_ONLY_PATHS = List.of(
            "/api/public/**",
            "/mcp",
            "/mcp/**",
            "/.well-known/**",
            "/oauth2/token",
            "/oauth2/revoke",
            "/oauth2/introspect",
            "/oauth2/jwks",
            "/connect/register"
    );

    public static CorsConfigurationSource create(List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration tokenOnly = tokenOnlyConfig();
        for (String path : TOKEN_ONLY_PATHS) {
            source.registerCorsConfiguration(path, tokenOnly);
            source.registerCorsConfiguration("/" + TenantPathMarker.SEGMENT + "/*" + path, tokenOnly);
        }
        source.registerCorsConfiguration("/**", cookieConfig(allowedOrigins));
        return source;
    }

    private static CorsConfiguration tokenOnlyConfig() {
        CorsConfiguration config = baseConfig();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowCredentials(false);
        return config;
    }

    private static CorsConfiguration cookieConfig(List<String> allowedOrigins) {
        CorsConfiguration config = baseConfig();
        config.setAllowedOriginPatterns(allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(StringUtils::hasText).map(String::trim).toList());
        config.setAllowCredentials(true);
        return config;
    }

    private static CorsConfiguration baseConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(Arrays.asList(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_LENGTH));
        config.setMaxAge(3600L); // 캐시 1시간
        return config;
    }
}
