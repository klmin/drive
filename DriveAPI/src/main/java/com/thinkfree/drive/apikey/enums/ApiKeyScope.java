package com.thinkfree.drive.apikey.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API Key 에 부여되는 scope 의 단일 출처 — 발급 검증, DB 저장, 인증 필터의 권한 매핑이 모두 이 값을 쓴다.
 * 네이밍은 "리소스:행위" 형식으로, 일반 API 는 api:* 를 쓴다 ({@code OAuth2Scope} 와 동일).
 * <p>
 * value 는 DB(tb_api_key.scope)에 저장되고 외부 API 명세서에도 그대로 쓰인다.
 * 값을 바꾸면 이미 발급된 키가 동작하지 않는다.
 * 여러 개일 때는 공백으로 이어 붙이며, 변환은 {@link #join}/{@link #parse} 로만 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ApiKeyScope {

    READ("api:read"),
    WRITE("api:write"),
    /** MCP(/mcp) 접근용 — 값은 OAuth2Scope와 동일해야 한다. MCP 체인 게이트·McpScopeGuard가 이 authority를 본다 */
    MCP_READ("mcp:read"),
    MCP_WRITE("mcp:write"),
    ;

    /** Spring Security 관례 — scope 는 SCOPE_ 프리픽스 authority 로 매핑된다 */
    public static final String AUTHORITY_PREFIX = "SCOPE_";

    private static final String DELIMITER = " ";

    private static final List<String> SUPPORTED_VALUES =
            Arrays.stream(values()).map(ApiKeyScope::getValue).toList();

    /**
     * 발급 시 허용되는 조합 — 읽기 전용 / 읽기+쓰기 두 가지뿐이다.
     * 화면의 선택지가 그대로 계약이며, 백엔드는 값을 만들지 않고 이 표에 맞는지만 본다.
     * scope 가 늘어나면 이 상수만 고치면 된다.
     */
    private static final List<Set<ApiKeyScope>> ALLOWED_COMBINATIONS = List.of(
            EnumSet.of(READ, MCP_READ),
            EnumSet.of(READ, WRITE, MCP_READ, MCP_WRITE)
    );

    private final String value;

    /** 권한 검사용 표기 (예: SCOPE_api:read) */
    public String authority() {
        return AUTHORITY_PREFIX + value;
    }

    /** 발급 화면 선택지·명세서용 */
    public static List<String> supportedValues() {
        return SUPPORTED_VALUES;
    }

    public static boolean isSupported(String value) {
        return SUPPORTED_VALUES.contains(value);
    }

    public static ApiKeyScope from(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    /** 발급 요청으로 들어온 조합이 허용된 것인지 — 값 하나하나가 유효해도 조합이 아니면 거부한다 */
    public static boolean isAllowedCombination(Set<ApiKeyScope> scopes) {
        return ALLOWED_COMBINATIONS.contains(scopes);
    }

    /**
     * DB 저장용 문자열로 변환. 선언 순서로 정렬하고 중복을 제거해 같은 조합이 항상 같은 문자열이 되게 한다.
     *
     * @return 공백 구분 문자열. 비어 있으면 null — 발급 경로는 필수·조합 검증을 거치므로 걸리지 않는다
     */
    public static String join(Collection<ApiKeyScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(scopes).stream()
                .map(ApiKeyScope::getValue)
                .collect(Collectors.joining(DELIMITER));
    }

    /** DB 문자열 → scope 집합. 인증 경로에서 쓰이므로 알 수 없는 값은 인증을 깨뜨리지 않도록 무시한다 */
    public static Set<ApiKeyScope> parse(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return EnumSet.noneOf(ApiKeyScope.class);
        }
        return Arrays.stream(scopes.trim().split("\\s+"))
                .map(ApiKeyScope::from)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiKeyScope.class)));
    }
}
