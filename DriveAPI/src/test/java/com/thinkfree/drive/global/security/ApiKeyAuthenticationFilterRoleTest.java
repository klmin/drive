package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.properties.SuperAdminExternalProperty;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.global.ratelimit.ApiClientRateLimitGuard;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 외부 체인 API Key 필터의 역할 — 키 역할(tb_api_key.role_type)로 인증하되 대상 계정이 그 역할을 보유해야 한다(회수 시 401).
 * 슈퍼관리자 정적 키는 SUPER_ADMIN 고정. 키 조회·테넌트 해석은 통과로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyAuthenticationFilter — 키 역할 보유 확인")
class ApiKeyAuthenticationFilterRoleTest {

    private static final String KEY = "B6u6gFwHhyBum9Js_LRrcISG54dEC-HpZ45E4gtlvsQ";
    private static final int TENANT = 1;
    private static final int USER_SEQ = 10;

    @Mock private ApiKeyFindService apiKeyFindService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private UserFindService userFindService;
    @Mock private SuperAdminExternalProperty superAdminExternalProperty;
    @Mock private ApiClientRateLimitGuard rateLimitGuard;
    @Mock private FilterChain chain;

    @InjectMocks
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(superAdminExternalProperty.matches(anyString())).willReturn(false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void key(RoleType role, Integer keyTenant) {
        given(apiKeyFindService.findActiveByApiKey(KEY, TENANT)).willReturn(new ApiKeyData(1, keyTenant, USER_SEQ, "k", null, KEY,
                role, role == RoleType.USER ? "api:read" : null, ApiKeyStatus.ACTIVE, Instant.now().plusSeconds(600), null, 1, Instant.now(), false));
    }

    /** 대상 계정 — 키 역할은 계정이 아니라 키(tb_api_key.role_type)에서 */
    private UserDetailData account(Integer tenantSeq) {
        UserDetailData user = mock(UserDetailData.class);
        given(user.getUserSeq()).willReturn(USER_SEQ);
        given(user.getUserId()).willReturn("u@test.com");
        given(user.getUserName()).willReturn("u");
        given(user.getTenantSeq()).willReturn(tenantSeq);
        return user;
    }

    private Authentication run() throws Exception {
        return run(null);
    }

    /** roleHeader 가 null 이면 X-Drive-Role 없이 */
    private Authentication run(String roleHeader) throws Exception {
        return run(filter, roleHeader);
    }

    private Authentication run(ApiKeyAuthenticationFilter target, String roleHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/v1/users/exists");
        request.addHeader("Authorization", "Bearer " + KEY);
        if (roleHeader != null) {
            request.addHeader(AuthCookieManager.ROLE_HEADER, roleHeader);
        }
        target.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain, atLeastOnce()).doFilter(any(), any());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("키 역할 보유 — 키 역할 1개 + scope 로 인증, EXTERNAL")
    void heldKeyRoleAuthenticates() throws Exception {
        key(RoleType.USER, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.USER)).willReturn(account);

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "SCOPE_api:read");
        assertThat(((CustomUserDetails) auth.getPrincipal()).getAuthSource()).isEqualTo(AuthSource.EXTERNAL);
    }

    @Test
    @DisplayName("키 역할 회수(또는 비활성) — 조회 실패라 인증 없음(entry point 401)")
    void revokedKeyRoleNotAuthenticated() throws Exception {
        key(RoleType.ADMIN, TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.ADMIN)).willThrow(new ApiRuntimeException(UserErrorCode.USER_NOT_FOUND));

        assertThat(run()).isNull();
    }

    @Test
    @DisplayName("슈퍼관리자 키 — 키 역할로 테넌트 예외 판단(계정 테넌트 null 이어도 인증)")
    void superAdminKeyTenantException() throws Exception {
        key(RoleType.SUPER_ADMIN, TENANT);
        UserDetailData superAdmin = account(null);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.SUPER_ADMIN)).willReturn(superAdmin);

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("일반 키 테넌트 불일치 — 인증 없음")
    void tenantMismatchNotAuthenticated() throws Exception {
        key(RoleType.USER, TENANT);
        UserDetailData otherTenant = account(2);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.USER)).willReturn(otherTenant);

        assertThat(run()).isNull();
    }

    @Test
    @DisplayName("X-Drive-Role 이 키 역할과 같음 — 인증")
    void roleHeaderMatchesKeyRole() throws Exception {
        key(RoleType.ADMIN, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.ADMIN)).willReturn(account);

        Authentication auth = run("ADMIN");

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("USER 키 + X-Drive-Role: ADMIN — 인증 없음, 마지막 사용일 갱신 안 함")
    void userKeyWithAdminHeaderNotAuthenticated() throws Exception {
        key(RoleType.USER, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.USER)).willReturn(account);

        assertThat(run("ADMIN")).isNull();
        verify(apiKeyService, never()).updateLastUsedDate(anyString(), any());
    }

    @Test
    @DisplayName("ADMIN 키 + X-Drive-Role: USER — 인증 없음")
    void adminKeyWithUserHeaderNotAuthenticated() throws Exception {
        key(RoleType.ADMIN, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.ADMIN)).willReturn(account);

        assertThat(run("USER")).isNull();
    }

    @Test
    @DisplayName("X-Drive-Role 이 역할 이름이 아님 — 인증 없음")
    void unknownRoleHeaderNotAuthenticated() throws Exception {
        key(RoleType.USER, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.USER)).willReturn(account);

        assertThat(run("user")).isNull();
    }

    @Test
    @DisplayName("앞에서 쿠키 인증이 끝난 요청 — 키를 조회·갱신하지 않고 기존 인증 유지")
    void alreadyAuthenticatedSkipsApiKey() throws Exception {
        Authentication cookieAuth = new UsernamePasswordAuthenticationToken("cookie-user", "", List.of());
        SecurityContextHolder.getContext().setAuthentication(cookieAuth);

        assertThat(run()).isSameAs(cookieAuth);
        verify(apiKeyFindService, never()).findActiveByApiKey(anyString(), any());
        verify(apiKeyService, never()).updateLastUsedDate(anyString(), any());
    }

    /** 내부 체인용 필터 — SUPER_ADMIN 역할 키 거부 */
    private ApiKeyAuthenticationFilter internalFilter() {
        return ApiKeyAuthenticationFilter.rejectingSuperAdminKey(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, rateLimitGuard);
    }

    @Test
    @DisplayName("내부 체인 — 슈퍼관리자 정적 키는 인증 없음, 계정 조회 안 함")
    void internalRejectsStaticSuperAdminKey() throws Exception {
        given(superAdminExternalProperty.matches(KEY)).willReturn(true);

        assertThat(run(internalFilter(), null)).isNull();
        verify(userFindService, never()).findEarliestActiveSuperAdmin();
    }

    @Test
    @DisplayName("내부 체인 — DB 의 SUPER_ADMIN 역할 키는 인증 없음, 마지막 사용일 갱신 안 함")
    void internalRejectsSuperAdminRoleKey() throws Exception {
        key(RoleType.SUPER_ADMIN, TENANT);
        UserDetailData superAdmin = account(null);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.SUPER_ADMIN)).willReturn(superAdmin);

        assertThat(run(internalFilter(), null)).isNull();
        verify(apiKeyService, never()).updateLastUsedDate(anyString(), any());
    }

    @Test
    @DisplayName("내부 체인 — USER·ADMIN 키는 그대로 인증")
    void internalAllowsAdminKey() throws Exception {
        key(RoleType.ADMIN, TENANT);
        UserDetailData account = account(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.ADMIN)).willReturn(account);

        Authentication auth = run(internalFilter(), null);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("슈퍼관리자 정적 키 — SUPER_ADMIN 고정")
    void staticSuperAdminKey() throws Exception {
        given(superAdminExternalProperty.matches(KEY)).willReturn(true);
        UserDetailData superAdmin = account(null);
        given(userFindService.findEarliestActiveSuperAdmin()).willReturn(superAdmin);

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_SUPER_ADMIN")
                .noneMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_USER"));
    }
}
