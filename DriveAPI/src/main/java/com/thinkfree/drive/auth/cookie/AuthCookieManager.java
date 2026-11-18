package com.thinkfree.drive.auth.cookie;

import com.thinkfree.drive.auth.properties.JwtProperty;
import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import com.thinkfree.drive.global.security.SecurityPaths;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 로그인 access·refresh 토큰을 HttpOnly 쿠키로 발급·삭제·조회한다.
 * <ul>
 *     <li>이름 — 로그인 화면(역할)별 고정 {@code DRIVE_AT_{역할}}·{@code DRIVE_RT_{역할}}. 테넌트는 이름이 아니라 Path 로 나눈다</li>
 *     <li>Path — access 는 {@code {contextPath}/}, refresh 는 {@code {contextPath}/api/v1/token/reissue}(재발급 요청에만 실림).
 *         PATH 모드는 {@link TenantPathRequestWrapper} 가 contextPath 에 {@code /t/{slug}} 를 붙여 테넌트 경로가 되고 SUBDOMAIN 모드는 {@code /}.
 *         슈퍼관리자는 테넌트에 속하지 않아 접두 없는 서블릿 contextPath 를 쓴다</li>
 *     <li>Domain 은 지정하지 않는다(host-only) — 지정하면 SUBDOMAIN 모드에서 다른 테넌트 호스트로 전송된다</li>
 *     <li>쿠키 선택 — 프론트가 화면 경로로 정한 {@value #ROLE_HEADER} 헤더 이름의 쿠키만 읽는다. 헤더가 없으면 쿠키 인증을 하지 않는다</li>
 * </ul>
 * 쿠키 역할은 토큰 role 과 다를 수 있다 — 슈퍼관리자의 테넌트 관리자 진입은 SUPER_ADMIN 토큰을 ADMIN 쿠키(테넌트 Path)로 발급한다.
 */
@Component
public class AuthCookieManager {

    public static final String ROLE_HEADER = "X-Drive-Role";

    private static final String ACCESS_COOKIE_PREFIX = "DRIVE_AT_";
    private static final String REFRESH_COOKIE_PREFIX = "DRIVE_RT_";

    private final AuthCookieProperty property;
    private final JwtProperty jwtProperty;

    public AuthCookieManager(AuthCookieProperty property, JwtProperty jwtProperty) {
        this.property = property;
        this.jwtProperty = jwtProperty;
    }

    /** access·refresh 쿠키를 함께 내려준다. 같은 이름·Path 의 기존 쿠키는 브라우저가 덮어쓴다. */
    public void issue(HttpServletRequest request, HttpServletResponse response, RoleType cookieRole,
                      String accessToken, String refreshToken) {
        addCookie(response, cookie(accessCookieName(cookieRole), accessToken, accessPath(request, cookieRole), jwtProperty.accessExp()));
        addCookie(response, cookie(refreshCookieName(cookieRole), refreshToken, refreshPath(request, cookieRole), jwtProperty.refreshExp()));
    }

    /** 발급 때와 같은 이름·Path 로 {@code Max-Age=0} 을 내려 access·refresh 쿠키를 지운다. 다른 역할·테넌트 쿠키는 남는다. */
    public void expire(HttpServletRequest request, HttpServletResponse response, RoleType cookieRole) {
        addCookie(response, cookie(accessCookieName(cookieRole), "", accessPath(request, cookieRole), Duration.ZERO));
        addCookie(response, cookie(refreshCookieName(cookieRole), "", refreshPath(request, cookieRole), Duration.ZERO));
    }

    /** {@value #ROLE_HEADER} 헤더로 읽을 쿠키의 역할을 정한다. 헤더가 없거나 역할 이름이 아니면 empty. */
    public static Optional<RoleType> resolveCookieRole(HttpServletRequest request) {
        String header = request.getHeader(ROLE_HEADER);
        if (!StringUtils.hasText(header)) {
            return Optional.empty();
        }
        String name = header.trim();
        return Arrays.stream(RoleType.values())
                .filter(role -> role.name().equals(name))
                .findFirst();
    }

    public static Optional<String> resolveAccessToken(HttpServletRequest request, RoleType cookieRole) {
        return findCookieValue(request, accessCookieName(cookieRole));
    }

    public static Optional<String> resolveRefreshToken(HttpServletRequest request, RoleType cookieRole) {
        return findCookieValue(request, refreshCookieName(cookieRole));
    }

    public static String accessCookieName(RoleType cookieRole) {
        return ACCESS_COOKIE_PREFIX + suffixOf(cookieRole);
    }

    public static String refreshCookieName(RoleType cookieRole) {
        return REFRESH_COOKIE_PREFIX + suffixOf(cookieRole);
    }

    private static String suffixOf(RoleType cookieRole) {
        return switch (cookieRole) {
            case SUPER_ADMIN -> "SUPER";
            case ADMIN -> "ADMIN";
            case USER -> "USER";
            case ANONYMOUS -> "ANON";
        };
    }

    private static String accessPath(HttpServletRequest request, RoleType cookieRole) {
        return basePath(request, cookieRole) + "/";
    }

    private static String refreshPath(HttpServletRequest request, RoleType cookieRole) {
        return basePath(request, cookieRole) + SecurityPaths.TOKEN_REISSUE;
    }

    /** 슈퍼관리자는 테넌트 접두로 들어온 요청이어도 서블릿 contextPath(접두 없음), 그 외는 요청의 contextPath(PATH 모드면 접두 포함). */
    private static String basePath(HttpServletRequest request, RoleType cookieRole) {
        String contextPath = cookieRole == RoleType.SUPER_ADMIN
                ? request.getServletContext().getContextPath()
                : request.getContextPath();
        return contextPath == null ? "" : contextPath;
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(property.secure())
                .sameSite(property.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 같은 이름이 여러 개 실리면 브라우저가 Path 가 긴 쿠키를 앞에 두므로 첫 번째 값을 쓴다. */
    private static Optional<String> findCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }
}
