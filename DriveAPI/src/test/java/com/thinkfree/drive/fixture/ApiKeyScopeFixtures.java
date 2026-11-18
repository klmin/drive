package com.thinkfree.drive.fixture;

import java.util.List;

/**
 * 발급 요청에 실을 scope 조합과 그 저장 형식.
 *
 * <p>허용 조합은 읽기 전용 / 읽기+쓰기 두 가지뿐이고 백엔드는 값을 보정하지 않는다.
 * 조합을 테스트마다 직접 나열하면 규칙이 바뀔 때 흩어진 리터럴을 전부 찾아다녀야 하므로 여기로 모은다.
 */
public class ApiKeyScopeFixtures {

    public static final List<String> READ_SCOPES = List.of("api:read", "mcp:read");
    public static final List<String> WRITE_SCOPES = List.of("api:read", "api:write", "mcp:read", "mcp:write");

    /** DB 저장 형식 — 선언 순서로 정렬된 공백 구분 문자열 */
    public static final String READ_SCOPE_VALUE = "api:read mcp:read";
    public static final String WRITE_SCOPE_VALUE = "api:read api:write mcp:read mcp:write";

    private ApiKeyScopeFixtures() {}
}
