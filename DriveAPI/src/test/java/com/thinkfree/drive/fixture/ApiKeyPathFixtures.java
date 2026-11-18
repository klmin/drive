package com.thinkfree.drive.fixture;

/**
 * API Key 엔드포인트 경로.
 *
 * <p>오타는 존재하지 않는 경로가 되어 404 로 끝나는데, 404 를 기대하는 테스트는 그대로 통과한다 —
 * 잘못된 이유로 초록불이 되는 것을 막으려고 상수로 둔다.
 *
 * <p>컨트롤러의 매핑 문자열을 공유하지 않고 따로 관리한다. 운영 경로가 바뀌면 이 상수도 고쳐야 하고,
 * 그 지점이 외부에 공개된 경로가 바뀐다는 걸 확인하는 자리가 된다.
 */
public class ApiKeyPathFixtures {

    public static final String ADMIN_BASE_URL       = "/api/v1/admins/api-keys";
    public static final String USER_BASE_URL        = "/api/v1/users/api-keys";
    public static final String SUPER_ADMIN_BASE_URL = "/api/v1/super-admin/api-keys";

    public static final String EXPIRE_DATE = "expire-date";
    public static final String ACTIVATE    = "activate";
    public static final String DEACTIVATE  = "deactivate";
    public static final String REISSUE     = "reissue";

    private ApiKeyPathFixtures() {}
}
