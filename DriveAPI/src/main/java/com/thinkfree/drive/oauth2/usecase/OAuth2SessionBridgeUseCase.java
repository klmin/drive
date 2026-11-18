package com.thinkfree.drive.oauth2.usecase;

import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.oauth2.OAuth2UserPrincipal;
import com.thinkfree.drive.oauth2.exception.OAuth2SessionBridgeException;
import com.thinkfree.drive.oauth2.response.OAuth2SessionBridgeResponse;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

/**
 * C안 브리지 — 기존 Drive 로그인 JWT(HS256 access)를 AS 세션 인증으로 교환한다
 * (MCP_OAUTH_BRIDGE_FLOW.md, MCP_OAUTH_FLOW.md §3.3).
 *
 * 전제: 미인증 /oauth2/authorize 진입 시 Spring Security가 원 요청을 세션
 * RequestCache에 저장하고 프론트 로그인 라우트로 302한 상태. 프론트는 기존
 * 로그인(local/OIDC/JWT — 어떤 방식이든)으로 Drive JWT를 확보한 뒤 여기를 호출한다.
 *
 * 하는 일: ① Drive JWT 검증(기존 /mcp 필터의 HS256 경로와 동일 절차 — 서명·만료,
 * ACCESS 타입, 블랙리스트) ② 토큰 사용자가 현재 Host 테넌트의 활성 유저인지 확인
 * ③ 세션 고정 방지 후 OAuth2UserPrincipal로 세션 SecurityContext 확립
 * ④ RequestCache의 원본 authorize URL 반환 — 프론트가 그대로 이동하면 SAS가
 * 이번엔 인증된 요청으로 처리해 코드를 발급한다.
 *
 * 로그인 방식(local/oidc/jwt) 분기는 여기서 하지 않는다 — 이미 기존 로그인
 * API가 테넌트 정책대로 검증을 끝냈고, 그 결과물(JWT)만 신뢰 근거로 쓴다.
 *
 * principal은 CustomUserDetails가 아닌 OAuth2UserPrincipal — 이 세션 인증이
 * SAS의 oauth2_authorization.attributes에 Jackson 직렬화되기 때문 (§6 함정 7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2SessionBridgeUseCase {

    /** SavedRequest가 authorize 요청일 때만 브리지 허용 — 다른 경로의 잔존 SavedRequest 오용 방지 */
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";

    /**
     * MCP OAuth 동작 역할 — 정하는 곳은 여기 한 곳. principal → 토큰 role 클레임(커스터마이저) → /mcp 필터 보유 확인으로 이어진다.
     * 진입이 사용자 로그인(로그인 방식 검증·MFA)뿐이라 USER 고정 — 관리자 툴은 관리자 API Key 로 쓴다.
     */
    private static final RoleType OAUTH_ROLE = RoleType.USER;

    private final JwtProvider jwtProvider;
    private final BlacklistTokenFindService blacklistTokenFindService;
    private final UserFindService userFindService;
    private final UserRoleFindService userRoleFindService;

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /** @param accessToken 사용자 로그인 쿠키의 access 토큰(없으면 null) */
    public OAuth2SessionBridgeResponse bridge(String accessToken,
                                              HttpServletRequest request, HttpServletResponse response) {
        if (accessToken == null) {
            throw OAuth2SessionBridgeException.invalidToken("Missing token");
        }
        int userSeq = verifyDriveAccessToken(accessToken);
        UserDetailData user = verifyTenantUser(userSeq);
        verifyOAuthRole(userSeq);

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null || !savedRequest.getRedirectUrl().contains(AUTHORIZE_PATH)) {
            throw OAuth2SessionBridgeException.noPendingAuthorization();
        }

        establishSession(request, response, user);

        log.info("[OAuth2SessionBridge] 세션 브리지 성공 - userSeq : {}, tenantSeq : {}", user.getUserSeq(), user.getTenantSeq());
        return new OAuth2SessionBridgeResponse(savedRequest.getRedirectUrl());
    }

    /** 기존 Drive JWT 검증 절차(McpBearerTokenAuthenticationFilter의 HS256 경로와 동일) */
    private int verifyDriveAccessToken(String token) {
        if (!jwtProvider.validateToken(token)) {
            throw OAuth2SessionBridgeException.invalidToken("Invalid or expired token");
        }
        if (!jwtProvider.isTokenType(token, JwtType.ACCESS)) {
            throw OAuth2SessionBridgeException.invalidToken("Not an access token");
        }
        if (blacklistTokenFindService.existsByTokenHash(new BlacklistFindQuery(TokenHasher.hash(token)))) {
            throw OAuth2SessionBridgeException.invalidToken("Blacklisted token");
        }
        if (jwtProvider.getRole(token) != OAUTH_ROLE) {
            throw OAuth2SessionBridgeException.invalidToken("Not a user login token");
        }
        return jwtProvider.getId(token);
    }

    /** OAuth 동작 역할을 지금도 보유하는지 — 로그인 뒤 회수된 계정은 여기서 로그인 폼으로 돌려보낸다 */
    private void verifyOAuthRole(int userSeq) {
        if (!userRoleFindService.holds(userSeq, OAUTH_ROLE)) {
            log.info("[OAuth2SessionBridge] OAuth 역할 미보유 - userSeq={}, role={}", userSeq, OAUTH_ROLE);
            throw OAuth2SessionBridgeException.invalidToken("User does not hold role " + OAUTH_ROLE);
        }
    }

    /** 토큰의 사용자가 현재 서브도메인(테넌트)의 활성 유저인지 — 교차 테넌트 브리지 차단 */
    private UserDetailData verifyTenantUser(int userSeq) {
        Integer tenantSeq = TenantContext.get();
        if (tenantSeq == null) {
            throw OAuth2SessionBridgeException.invalidToken("Tenant not resolved for request host");
        }
        try {
            return userFindService.findActiveDetailInTenant(userSeq, tenantSeq);
        } catch (ApiRuntimeException e) {
            log.info("[OAuth2SessionBridge] 테넌트 유저 검증 실패 - userSeq={}, tenantSeq={}", userSeq, tenantSeq);
            throw OAuth2SessionBridgeException.invalidToken("User not found in tenant");
        }
    }

    /**
     * 세션에 로그인 인증을 확립 — 이후 authorize 재진입 때 SAS 체인이 이 컨텍스트를 읽는다.
     * changeSessionId는 인증 승격 시점의 세션 고정(session fixation) 공격 방지.
     */
    private void establishSession(HttpServletRequest request, HttpServletResponse response, UserDetailData user) {
        request.changeSessionId();

        OAuth2UserPrincipal principal = OAuth2UserPrincipal.from(user, OAUTH_ROLE);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextRepository.saveContext(context, request, response);
    }
}
