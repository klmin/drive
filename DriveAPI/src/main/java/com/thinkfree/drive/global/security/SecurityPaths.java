package com.thinkfree.drive.global.security;

import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;


/**
 * 보안 경로 카테고리. 각 카테고리별로 어떤 필터를 거치는지 한 곳에서 관리한다.
 *
 * <h3>필터 체인 순서</h3>
 * Tenant → Jwt → Ip → TenantConsistency → (인가) — 익명 계정 경로 제한은 SecurityConfig 인가 규칙(AnonymousPathRegistry)
 *
 * <h3>카테고리별 필터 적용표</h3>
 * <pre>
 * | 카테고리                | TenantSet | Jwt  | Ip   | AnonymousAccess | TenantConsistency | 비고                                                  |
 * |------------------------|-----------|------|------|-----------------|-------------------|------------------------------------------------------|
 * | SYSTEM_PUBLIC          | skip      | skip | skip | skip            | skip              | WebSecurity ignoring 으로 시큐리티 체인 자체 우회       |
 * | TENANT_PUBLIC          | set       | skip | skip | skip            | skip              | 테넌트 의존 공개 경로 (인증 불필요)                      |
 * | HTTP_STORAGE_ENDPOINTS | set | skip | skip | skip | skip | TFO HTTP Storage 콜백. 테넌트 주소를 해석한 뒤 요청 서명과 문서 세션·현재 파일 권한을 검증한다. |
 * | PROFILE_IMAGE_ENDPOINTS| set       | skip | skip | skip            | skip              | 프로필 이미지 — 토큰 없는 외부 시스템(WebOffice 어댑터의 커서 이미지 등)이 URL 로 가져감. 이미지만 반환 |
 * | LOGIN_ENDPOINTS        | set       | skip | 검증 | skip            | skip              | 로그인 진입점 — 인증 전이지만 IP 화이트리스트는 검증, 공개 링크 토큰 기반 익명 로그인 |
 * | LOGOUT_ENDPOINTS       | set/없음   | skip | skip | skip            | skip              | 로그아웃 — 만료된 토큰이어도 쿠키는 지워야 해 인증 없이 받고, IP 정책으로 막혀도 로그아웃은 되도록 IP 검사도 면제. 토큰이 유효할 때만 블랙리스트 등록 |
 * | SUPER_ADMIN_API        | skip*     | 검증 | 검증 | skip            | skip(SUPER_ADMIN) | 슈퍼어드민 전용(SUPER_ADMIN_LOGIN·/api/v1/super-admin/**·외부판). 접두 없는 요청은 TenantFilter 가 판별을 건너뛰어 TenantContext 없이 진행(usecase 미참조, TB_USER.tenant_seq NULL). *PATH 접두 /t/{slug}/api/v1/super-admin/** 은 평소처럼 그 테넌트가 set 되나 무해 |
 * | TENANT_OPTIONAL        | set/없음   | (경로별) | (경로별) | (경로별)    | (경로별)          | 슈퍼어드민 화면도 접두 없이 부르는 공유 경로(branding·logout·token/reissue·GET users·profile-image). 테넌트가 특정되면 set, 매치·fallback 모두 없으면 404 대신 TenantContext 없이 진행. usecase 는 null 이면 기본값(브랜딩) 또는 미참조(나머지) |
 * | CUSTOM_AUTH_ENDPOINTS  | set       | skip | 검증 | skip            | skip              | 자체 토큰/링크 기반 인증 (다운로드, mfa, sso 등)         |
 * | TENANT_CONSISTENCY_SKIP| set       | 검증 | 검증 | 검증            | skip              | 비즈니스 내부에서 cross-tenant 검증 (예: 공유 링크 접속)  |
 * | (그 외)                | set       | 검증 | 검증 | 검증            | 검증              | 일반 인증 사용자 API                                    |
 * </pre>
 *
 * <h3>주요 상수</h3>
 * <ul>
 *   <li>{@link #SYSTEM_PUBLIC} — {@code SecurityConfig.webSecurityCustomizer()} 와
 *       {@link com.thinkfree.drive.global.filter.TenantFilter#shouldNotFilter} 에서 사용</li>
 *   <li>{@link #SUPER_ADMIN_API} / {@link #isSuperAdminApi} — TenantFilter 의 {@code shouldNotFilter}(접두 없는 요청만 해당)</li>
 *   <li>{@link #TENANT_OPTIONAL} / {@link #isTenantOptional} — TenantFilter 가 매치·fallback 실패 시 404 대신 tenant-less 로 통과시키는 경로</li>
 *   <li>{@link #AUTH_WHITELIST} / {@link #AUTH_WHITELIST_MATCHER} —
 *       {@code SecurityConfig.requestMatchers(...).permitAll()},
 *       JwtAuthenticationFilter / TenantConsistencyFilter 의 {@code shouldNotFilter} 에서 재사용</li>
 *   <li>{@link #IP_WHITELIST} / {@link #IP_WHITELIST_MATCHER} — IpAccessFilter 의 {@code shouldNotFilter}</li>
 *   <li>{@link #TENANT_CONSISTENCY_SKIP} — TenantConsistencyFilter 가
 *       AUTH_WHITELIST 외 추가로 면제할 경로 (path + HTTP method)</li>
 * </ul>
 *
 * <h3>새 경로 추가 시</h3>
 * <ol>
 *   <li>해당 경로의 카테고리 결정 (위 표 기준)</li>
 *   <li>적절한 String[] 배열에 패턴 추가 — 매처는 자동으로 갱신됨</li>
 *   <li>기존 카테고리에 맞지 않으면 새 카테고리/매처를 추가하고 표도 함께 갱신</li>
 * </ol>
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class SecurityPaths {
    
    /** 인프라/개발 도구 — 테넌트/보안 무관 */
    private static final String[] INFRA_PUBLIC = {
            "/h2-console/**",
            "/error",
            "/swagger-ui/**",
            "/api-docs/**",
            "/actuator/**"
    };
    
    /** 시스템 공용 — 테넌트/보안 무관 (비즈니스 + 표준 엔드포인트) */
    private static final String[] BUSINESS_PUBLIC = {
            "/api/v1/common/ping",
            "/api/v1/geoip/country-code",
            "/api/v1/build/**"
    };

    /** 모든 보안/테넌트 필터 skip 대상 */
    public static final String[] SYSTEM_PUBLIC = concat(INFRA_PUBLIC, BUSINESS_PUBLIC);

    /** 테넌트 의존 공개 — Tenant set. Jwt/Ip/TenantConsistency skip */
    private static final String[] TENANT_PUBLIC = {
            "/api/v1/branding",
            "/api/v1/auth/login-config",
            "/api/v1/auth/sso/callback/oidc"
    };
    
    public static final String SHARE_LINK_ACCESS = "/api/v1/shares/link/*";

    public static final String SUPER_ADMIN_LOGIN = "/api/v1/auth/super-admin/login";
    public static final String ADMIN_LOGIN = "/api/v1/auth/admin/login";
    public static final String USER_LOGIN = "/api/v1/auth/login";

    public static final String ANONYMOUS_LOGIN = "/api/v1/auth/anonymous/login";

    public static final String LOGOUT = "/api/v1/auth/logout";
    public static final String TOKEN_REISSUE = "/api/v1/token/reissue";

    /** 로그인, 공개 링크 토큰 기반 익명 로그인 — Tenant set. Jwt/TenantConsistency skip. Ip 검증 */
    private static final String[] LOGIN_ENDPOINTS = { SUPER_ADMIN_LOGIN, ADMIN_LOGIN, USER_LOGIN, ANONYMOUS_LOGIN };

    /** 로그아웃 — 쿠키의 토큰을 직접 읽는다. Jwt/Ip/TenantConsistency skip(테넌트는 TENANT_OPTIONAL 규칙) */
    private static final String[] LOGOUT_ENDPOINTS = { LOGOUT };

    /**
     * 슈퍼어드민 전용 API — 테넌트 판별 skip(접두 없는 요청). 슈퍼어드민은 테넌트 소속이 아니고(TB_USER.tenant_seq NULL)
     * usecase 도 TenantContext 를 읽지 않으므로, 다중 테넌트 배포(fallback 없음)에서 주소로 테넌트를 특정하지 않아도 진행해야 한다.
     * 하위 필터는 SUPER_ADMIN 이면 null 테넌트를 허용한다(IpAccessFilter·TenantBoundaryFilter). 외부 API 판은 슈퍼어드민 외부 키
     * 분기(ApiKeyAuthenticationFilter)가 테넌트를 쓰지 않으며, 테넌트 키로 이 경로를 치면 테넌트 미해결 401 이 정상.
     * <p>
     * 슈퍼어드민 프론트 라우트({@code /superadmin/**})는 여기 없다 — 정적 번들 최상위 세그먼트라 {@code StaticBundleIndex} 스킵이 담당.
     */
    private static final String[] SUPER_ADMIN_API = {
            SUPER_ADMIN_LOGIN,
            "/api/v1/super-admin/**",
            "/api/public/v1/super-admin/**"
    };

    /**
     * 테넌트 선택 경로 — 슈퍼어드민 화면도 접두 없이 부르는 공유 엔드포인트. 주소로 테넌트가 특정되면 평소처럼 set 되고,
     * 매치·fallback 이 모두 없을 때만 404 대신 TenantContext 없이 진행한다(다중 테넌트 배포의 슈퍼어드민 포털용).
     * <ul>
     *   <li>브랜딩 메타·이미지 — usecase 가 null 테넌트면 기본 브랜딩(yml defaults·클래스패스 이미지)을 반환</li>
     *   <li>로그아웃·토큰 재발급·현재 사용자 조회({@code GET /api/v1/users})·프로필 이미지 — 토큰·userSeq 기준 로직이라 테넌트 미참조
     *       (슈퍼어드민 레이아웃의 이름·아바타가 이 셋을 쓴다). USER/ADMIN 이 테넌트 없이 오면
     *       TenantBoundaryFilter 가 403(주소 없는 잘못된 요청이라 종전 404 와 같은 의미)</li>
     * </ul>
     * 목록 밖 = 슈퍼어드민 화면이 부르지 않거나 테넌트 없이는 의미가 없는 것(예: {@code /api/v1/config} 는 프론트 Provider 가
     * SUPER_ADMIN 영역에서 호출을 생략, {@code config/license-status} 는 슈퍼어드민 로그인에서 호출 제거).
     * {@code shouldNotFilter} 에 넣지 않는 이유: SUBDOMAIN 모드는 모든 요청이 접두 없이 와서 skip 하면 정상 사용자의 테넌트 브랜딩을 잃는다.
     */
    private static final String[] TENANT_OPTIONAL = {
            "/api/v1/branding",
            "/branding/images/**",
            LOGOUT,
            TOKEN_REISSUE,
            "/api/v1/users",
            "/api/v1/users/profile-image",
            "/api/v1/users/*/profile-image"
    };

    /** 자체 토큰/링크 기반 — Tenant set. Jwt/TenantConsistency skip. Ip 검증 */
    private static final String[] CUSTOM_AUTH_ENDPOINTS = {
            "/api/v1/auth/mfa/**",
            "/api/v1/auth/sso/**",
            "/api/v1/auth/admin/entry-key/verify",
            "/api/v1/users/password/reset-link",
            "/api/v1/users/password/reset-link/verify",
            "/api/v1/users/password/reset-link/confirm",
            "/api/v1/files/download",
            "/api/v1/folders/download",
            "/api/v1/resources/download",
            "/api/v1/resources/*/versions/download",
            "/api/v1/users/*/profile-image"
    };

    /**
     * HTTP Storage 콜백 — TenantFilter가 경로 또는 호스트로 테넌트를 결정한다.
     * 일반 로그인 대신 해당 테넌트의 요청 서명과 서버에 저장한 문서 세션을 검증한다.
     */
    public static final String HTTP_STORAGE_API = "/api/v1/tfo-http-storage/v1/**";

    /**
     * WebOffice 어댑터 콜백 — Tenant(접두 없는 요청)/Jwt/Ip/TenantConsistency skip.
     * <p>
     * 호출자가 사용자 브라우저가 아니라 외부 시스템(WebOffice 어댑터 서버)이라
     * 사용자 IP 화이트리스트(IpAccessControlService)로 좁힐 수 없다.
     * 대신 비즈니스 레이어에서 리소스 permission 검증을 수행한다.
     */
    private static final String[] HTTP_STORAGE_ENDPOINTS = {
            // HTTP Storage callbacks verify the signed request and document session themselves.
            HTTP_STORAGE_API
    };

    /**
     * 프로필 이미지 — Tenant set. Jwt/Ip/TenantConsistency skip.
     * <p>
     * 토큰 없는 외부 시스템이 URL 로 가져간다(WebOffice 어댑터가 공동 편집 커서에 표시 — WebOfficeUseCase 가 URL 을 내려줌).
     * 어댑터 서버에서 오므로 사용자 IP 화이트리스트로 좁힐 수 없다. 이미지만 반환한다.
     */
    private static final String[] PROFILE_IMAGE_ENDPOINTS = {
            "/api/v1/users/*/profile-image",
            "/api/v1/users/anonymous-profile-image"
    };

    /** JwtAuthenticationFilter — 인증 면제 경로 */
    public static final String[] AUTH_WHITELIST = concat(LOGIN_ENDPOINTS, LOGOUT_ENDPOINTS, TENANT_PUBLIC, CUSTOM_AUTH_ENDPOINTS, HTTP_STORAGE_ENDPOINTS, PROFILE_IMAGE_ENDPOINTS);

    /** RequestMatcher 생성기 — Security 7에서 제거되는 AntPathRequestMatcher 대체 (Security 6.5+ 기본 매처) */
    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

    /** {@link #AUTH_WHITELIST} 의 RequestMatcher 형태. 필터의 shouldNotFilter 등에서 재사용. */
    public static final RequestMatcher AUTH_WHITELIST_MATCHER = new OrRequestMatcher(
            Arrays.stream(AUTH_WHITELIST)
                  .map(PATH::matcher)
                  .toArray(RequestMatcher[]::new)
    );

    /** 토큰 재발급 — JwtAuthenticationFilter 가 refresh 토큰을 받는 유일한 경로 */
    public static final RequestMatcher TOKEN_REISSUE_MATCHER = PATH.matcher(HttpMethod.POST, TOKEN_REISSUE);

    /**
     * 역할 공용 — 관리자·슈퍼관리자 화면도 부르는 비관리자 API(USER·ADMIN, SUPER_ADMIN 은 하이어라키로 통과).
     * 드라이브 API 는 USER 전용이라, 관리자 화면이 쓰는 것만 여기에 둔다. 로그아웃은 인증 없이 받으므로({@link #LOGOUT_ENDPOINTS}) 여기 없다.
     */
    public static final RequestMatcher COMMON_API = new OrRequestMatcher(
            PATH.matcher(HttpMethod.PATCH, "/api/v1/users"),
            PATH.matcher(HttpMethod.PATCH, "/api/v1/users/password"),
            PATH.matcher(HttpMethod.POST, "/api/v1/users/password/reset-link/me"),
            PATH.matcher(HttpMethod.GET, "/api/v1/users/exists"),
            PATH.matcher(HttpMethod.GET, "/api/v1/config/license-status")
    );

    /** 역할 공용 + 익명 — {@code @AnonymousAccessible} 이면서 관리자 화면도 부르는 API(USER·ADMIN·ANONYMOUS) */
    public static final RequestMatcher COMMON_API_WITH_ANONYMOUS = new OrRequestMatcher(
            TOKEN_REISSUE_MATCHER,
            PATH.matcher(HttpMethod.GET, "/api/v1/users"),
            PATH.matcher(HttpMethod.GET, "/api/v1/config"),
            PATH.matcher(HttpMethod.GET, "/api/v1/config/office"),
            PATH.matcher(HttpMethod.GET, "/api/v1/config/tenant")
    );

    /** IpAccessFilter — IP 검증 면제 경로(로그아웃은 IP 가 막혀도 세션을 끝낼 수 있게) */
    public static final String[] IP_WHITELIST = concat(LOGOUT_ENDPOINTS, TENANT_PUBLIC, HTTP_STORAGE_ENDPOINTS, PROFILE_IMAGE_ENDPOINTS);

    /** {@link #IP_WHITELIST} 의 RequestMatcher 형태. 필터의 shouldNotFilter 등에서 재사용. */
    public static final RequestMatcher IP_WHITELIST_MATCHER = new OrRequestMatcher(
            Arrays.stream(IP_WHITELIST)
                  .map(PATH::matcher)
                  .toArray(RequestMatcher[]::new)
    );

    /**
     * TenantConsistencyFilter — AUTH_WHITELIST 외 추가로 검증 면제할 경로.
     * <p>
     * 어노테이션({@code @CheckResourcePermission}, {@code @CheckSharePermission})이 걸린 핸들러는
     * 필터에서 자동으로 skip (Aspect가 권한 검증) 되고, 여기에는 어노테이션 없이 비즈니스 내부에서 권한을
     * 검증하거나 cross-tenant 접근이 정상인 엔드포인트만 (path + method) 로 명시한다.
     * <p>
     * AUTH_WHITELIST는 필터에서 별도로 검사하므로 여기에 포함하지 않는다.
     */
    public static final RequestMatcher TENANT_CONSISTENCY_SKIP = new OrRequestMatcher(
            PATH.matcher(HttpMethod.GET, SHARE_LINK_ACCESS)
    );

    private static String[] concat(String[]... arrays) {
        return Arrays.stream(arrays).flatMap(Arrays::stream).toArray(String[]::new);
    }
    
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public static boolean isSystemPublic(String path) {
        return Arrays.stream(SYSTEM_PUBLIC).anyMatch(p -> MATCHER.match(p, path));
    }

    /** 슈퍼어드민 전용 API 인지 — TenantFilter.shouldNotFilter 용(원본 요청 경로 기준이라 접두 붙은 요청은 매치되지 않는다). */
    public static boolean isSuperAdminApi(String path) {
        return Arrays.stream(SUPER_ADMIN_API).anyMatch(p -> MATCHER.match(p, path));
    }

    /** WebOffice 어댑터 콜백인지 — TenantFilter.shouldNotFilter 용(원본 요청 경로 기준이라 접두 붙은 요청은 매치되지 않는다). */


    /** 테넌트 선택 경로인지 — TenantFilter 가 매치·fallback 실패 시 tenant-less 통과 여부를 결정할 때 사용. */
    public static boolean isTenantOptional(String path) {
        return Arrays.stream(TENANT_OPTIONAL).anyMatch(p -> MATCHER.match(p, path));
    }
}
