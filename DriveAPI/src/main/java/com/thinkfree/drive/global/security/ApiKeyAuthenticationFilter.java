package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.utils.AuthorizationExtractor;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.properties.SuperAdminExternalProperty;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyFindService apiKeyFindService;
    private final ApiKeyService apiKeyService;
    private final UserFindService userFindService;
    private final SuperAdminExternalProperty superAdminExternalProperty;

    /** SUPER_ADMIN 역할 키(고정·DB) 허용 여부 — 외부 체인만 허용, 내부 체인은 {@link #rejectingSuperAdminKey} 로 생성 */
    private boolean superAdminKeyAllowed = true;

    /** 내부 체인용 — SUPER_ADMIN 역할 키는 인증하지 않는다(슈퍼관리자는 쿠키 로그인만) */
    public static ApiKeyAuthenticationFilter rejectingSuperAdminKey(ApiKeyFindService apiKeyFindService, ApiKeyService apiKeyService,
                                                                   UserFindService userFindService, SuperAdminExternalProperty superAdminExternalProperty) {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty);
        filter.superAdminKeyAllowed = false;
        return filter;
    }

    /** 인증 화이트리스트는 JwtAuthenticationFilter 와 같이 건너뜀 — 외부 경로는 해당 없음 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.AUTH_WHITELIST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 내부 체인에서 앞의 쿠키(JWT) 인증이 성공했으면 API Key 는 보지 않는다(쿠키 우선)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization Bearer 가 없으면 API Key 인증 대상 아님(미로그인 등) — 판단은 entry point
        var apiKeyOpt = AuthorizationExtractor.extractToken(request);
        if (apiKeyOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UsernamePasswordAuthenticationToken authentication;
            
            String apiKey = apiKeyOpt.get();
            
            if(superAdminExternalProperty.matches(apiKey)){
                verifySuperAdminKeyAllowed();
                verifyRoleHeader(request, RoleType.SUPER_ADMIN);
                // SUPER_ADMIN 보유 계정 중 가장 오래된 1명(tb_user_role 기준) — 역할은 그 조회 조건 그대로
                var superAdmin = userFindService.findEarliestActiveSuperAdmin();
                authentication = createAuthentication(ApiKeyScope.join(EnumSet.allOf(ApiKeyScope.class)), RoleType.SUPER_ADMIN.getAuthority(), null, superAdmin.getUserSeq(), superAdmin.getUserId(), superAdmin.getUserName());
            }else{
                
                // TenantFilter 가 주소(Host/경로)에서 미리 주입한 tenantSeq 와 함께 lookup —
                // 다른 테넌트의 키로는 절대 인증되지 않도록 lookup 단계에서 격리한다.
                Integer requestTenantSeq = TenantContext.get();

                // 조회 단계에서 status=ACTIVE 와 만료일(EXPIRE_DATE > NOW())까지 함께 걸러진다.
                // 만료는 status 에 EXPIRED 를 두지 않고 조회 시점 NOW() 비교로 판정한다 —
                // 만료 배치가 필요 없고, 만료일을 연장하면 다음 요청부터 즉시 되살아난다.
                ApiKeyData apiKeyData = apiKeyFindService.findActiveByApiKey(apiKey, requestTenantSeq);

                if (apiKeyData == null) {
                    throw new AuthenticationException("Invalid API Key") {};
                }

                // 테넌트 없는 요청은 슈퍼관리자 키만 허용 — 조회 쿼리의 NULL 비교로도 걸러지지만 격리 의도를 명시
                if (requestTenantSeq == null && apiKeyData.getRoleType() != RoleType.SUPER_ADMIN) {
                    throw new AuthenticationException("Tenant not resolved for API Key") {};
                }

                if (RoleType.SUPER_ADMIN == apiKeyData.getRoleType()) {
                    verifySuperAdminKeyAllowed();
                }
                verifyRoleHeader(request, apiKeyData.getRoleType());
                
                apiKeyService.updateLastUsedDate(apiKey);
                
                UserDetailData actor = resolveActor(apiKeyData);
                
                authentication = createUserAuthentication(apiKeyData, actor);
            }
            
            applyAuthentication(authentication, request);

        } catch (AuthenticationException ex) {
            log.error("{ ApiKeyAuthenticationFilter } : 인증 오류 - {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void verifySuperAdminKeyAllowed() throws AuthenticationException {
        if (!superAdminKeyAllowed) {
            throw new AuthenticationException("Super admin API Key not allowed") {};
        }
    }

    /** {@value AuthCookieManager#ROLE_HEADER} 가 있으면 키 역할과 같아야 한다 — 없으면(스크립트 호출 등) 키 역할 그대로 */
    private static void verifyRoleHeader(HttpServletRequest request, RoleType keyRole) throws AuthenticationException {
        if (!StringUtils.hasText(request.getHeader(AuthCookieManager.ROLE_HEADER))) {
            return;
        }
        RoleType headerRole = AuthCookieManager.resolveCookieRole(request).orElse(null);
        if (headerRole != keyRole) {
            throw new AuthenticationException("X-Drive-Role mismatch - header : " + headerRole + ", key : " + keyRole) {};
        }
    }

    /**
     * 키의 대상 사용자({@code user_seq})를 actor 로 해석한다.
     * 이 값이 인증 actor — 키가 행세하는 계정이다.
     * <p>
     * 발급 시점에도 같은 항목을 검증하지만, <b>발급 후 계정이 정지될 수 있으므로</b> 매 요청 재확인한다.
     * 인증 시점 검사가 없으면 계정을 정지시켜도 그 사람 자격의 키가 계속 동작한다.
     * <ul>
     *     <li>대상 계정이 활성 상태이고 키 역할을 보유하는지 — 역할을 회수하면 그 역할 키는 다음 요청부터 401</li>
     *     <li>키의 {@code tenant_seq} == 대상 계정의 {@code tenant_seq} —
     *         예외는 슈퍼관리자가 본인을 대상으로 할 때 하나뿐(슈퍼관리자 계정에 테넌트가 없음).
     *         어기면 테넌트 교차 인증이 된다</li>
     * </ul>
     */
    private UserDetailData resolveActor(ApiKeyData apiKeyData) throws AuthenticationException {
        UserDetailData actor;
        try {
            // USER_SEQ = ? AND STATUS = 'ACTIVE' AND 키 역할 보유. 미존재·비활성·역할 회수면 예외
            actor = userFindService.findActiveDetailHoldingRole(apiKeyData.getUserSeq(), apiKeyData.getRoleType());
        } catch (ApiRuntimeException ex) {
            throw new AuthenticationException("API Key target user is missing or inactive - userSeq : " + apiKeyData.getUserSeq()) {};
        }

        if (actor == null) {
            throw new AuthenticationException("API Key target user not found - userSeq : " + apiKeyData.getUserSeq()) {};
        }

        // 키 역할 = 대상 계정의 보유 역할(위 조회에서 확인)
        boolean superAdmin = RoleType.SUPER_ADMIN == apiKeyData.getRoleType();
        if (!superAdmin && !Objects.equals(actor.getTenantSeq(), apiKeyData.getTenantSeq())) {
            throw new AuthenticationException("API Key tenant mismatch - key : " + apiKeyData.getTenantSeq()
                    + ", user : " + actor.getTenantSeq()) {};
        }

        return actor;
    }

    /**
     * authorities 에 role 과 scope 를 함께 싣는다.
     * <p>
     * 신원(userSeq·userId·userName)은 대상 계정에서, <b>권한(role)과 테넌트는 키에서</b> 가져온다.
     * 계정 값을 그대로 쓰면 키에 부여한 제한이 무시된다.
     * <p>
     * scope 는 role 과 무관하게 모든 키가 갖지만, 검사는 사용자 API 일부에만 적용된다.
     * 실제 차단은 외부 체인의 {@code authorizeHttpRequests} 에서 하고 여기서는 주입만 한다 —
     * 같은 컨트롤러 메서드가 내부·외부 두 경로에 등록되어 있어 메서드 보안으로는 경로를 구분할 수 없다.
     */
    private static UsernamePasswordAuthenticationToken createUserAuthentication(ApiKeyData apiKeyData, UserDetailData actor) {
        UserDetailDto userDetailDto = new UserDetailDto(
                actor.getUserSeq(),
                actor.getUserId(),
                actor.getUserName(),
                apiKeyData.getRoleType().getAuthority(),
                AuthSource.EXTERNAL,
                apiKeyData.getTenantSeq(),
                ApiKeyScope.parse(apiKeyData.getScope())
        );
        return createAuthentication(userDetailDto);
    }

    private static UsernamePasswordAuthenticationToken createAuthentication(UserDetailDto userDetailDto) {
        CustomUserDetails userDetails = new CustomUserDetails(userDetailDto);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    // NOTE: static-api-key 우회 분기 전용으로만 남은 얇은 위임 메서드.
    //       정상 경로는 createAuthentication(UserDetailDto)를 직접 쓴다. 우회 분기 정리 시 함께 제거 가능.
    private static UsernamePasswordAuthenticationToken createAuthentication(String scope, String roleType, Integer tenantSeq, Integer userSeq, String userId, String userName) {
        return createAuthentication(new UserDetailDto(userSeq, userId, userName, roleType, AuthSource.EXTERNAL, tenantSeq, ApiKeyScope.parse(scope)));
    }
    
    private void applyAuthentication(AbstractAuthenticationToken authentication, HttpServletRequest request) {
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    
}
