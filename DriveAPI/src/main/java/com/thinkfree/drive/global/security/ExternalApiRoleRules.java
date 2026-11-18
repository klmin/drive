package com.thinkfree.drive.global.security;

import com.thinkfree.drive.constants.DriveConstants;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * 외부 API({@code /api/public/v1/**}) 경로별 호출 가능 role — 한 곳에 두고 두 군데서 읽는다.
 * <ul>
 *   <li>{@code SecurityConfig.externalApiFilterChain}: 위에서부터 {@code requestMatchers(경로).hasAnyRole(role)} 로 등록하고,
 *       어느 규칙에도 맞지 않으면 {@link #DEFAULT_ROLES}(USER 키만 — ADMIN 키는 403)</li>
 *   <li>공개 OpenAPI 문서: operation 마다 {@code x-drive.roles} 로 내보낸다 — Developers 플레이그라운드가 User·Admin 탭을 나눈다</li>
 * </ul>
 * 규칙은 위에서부터 처음 맞는 것이 이긴다(Spring Security 와 같은 순서). 규칙을 바꾸면 인가와 문서가 함께 바뀐다.
 * role 이름은 {@code ROLE_} 접두사 없이 쓴다. {@code hasAnyRole} 은 RoleHierarchy(SUPER_ADMIN &gt; ADMIN)를 따르므로
 * ADMIN 규칙은 슈퍼관리자도 통과하지만, 문서에는 테넌트 키 기준 role 만 적는다.
 */
public final class ExternalApiRoleRules {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 경로({@code /api/public/v1} 포함 전체 경로 패턴)와 그 경로를 호출할 수 있는 role */
    public record Rule(String pattern, List<String> roles) {
        public Rule {
            roles = List.copyOf(roles);
        }

        public String[] roleArray() {
            return roles.toArray(String[]::new);
        }
    }

    /** 순서가 중요하다 — 라이선스 상태는 role 공용, 관리자·슈퍼관리자 경로는 각 role 만 */
    public static final List<Rule> RULES = List.of(
            new Rule(publicPath("/config/license-status"), List.of(USER, ADMIN)),
            new Rule(publicPath("/admins/**"), List.of(ADMIN)),
            new Rule(publicPath("/super-admin/**"), List.of(SUPER_ADMIN))
    );

    /** 어느 규칙에도 맞지 않는 외부 API — USER 키만 */
    public static final List<String> DEFAULT_ROLES = List.of(USER);

    private static final List<PathPattern> PATTERNS = RULES.stream()
            .map(rule -> PathPatternParser.defaultInstance.parse(rule.pattern()))
            .toList();

    private ExternalApiRoleRules() {
    }

    /**
     * 외부 API 경로(문서의 경로 템플릿 그대로, 예: {@code /api/public/v1/admins/users/{userSeq}})를 호출할 수 있는 role.
     * 템플릿 변수는 한 경로 조각으로 보고 맞춘다 — {@code /**} 규칙에는 영향이 없다.
     */
    public static List<String> rolesFor(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (int i = 0; i < RULES.size(); i++) {
            if (PATTERNS.get(i).matches(container)) {
                return RULES.get(i).roles();
            }
        }
        return DEFAULT_ROLES;
    }

    private static String publicPath(String path) {
        return DriveConstants.API_PUBLIC_V1_PREFIX + path;
    }
}
