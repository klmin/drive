package com.thinkfree.drive.mcp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetailsService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.mcp.util.McpUrls;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.oauth2.jwk.OAuth2KeyManager;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * /mcp 체인용 Bearer 토큰 인증 필터 (OAuth 1단계 — Resource Server 최소 구현).
 *
 * 허용 자격은 두 가지뿐이다 (MCP_OAUTH_FLOW.md §5.3-5 키 전략):
 * <ul>
 *   <li><b>RS256</b> — OAuth2 토큰. OAuth2KeyManager의 공개키로 검증. 주 경로.
 *       revoke는 SAS 저장소가 담당할 영역이라 블랙리스트 조회는 하지 않는다.</li>
 *   <li><b>API Key</b> — JWT 형태(점 3분절)가 아닌 불투명 Base62 문자열이면 이 경로.
 *       외부 API 체인(ApiKeyAuthenticationFilter)과 동일하게 Host 테넌트로 격리 조회하고,
 *       키에 저장된 scope 문자열을 role과 무관하게 그대로 authority 재료로 쓴다 —
 *       발급 시 모든 키가 mcp:* 를 함께 받으므로 role과 무관하게 체인 게이트를 통과한다.
 *       MCP 설정 JSON에 고정 헤더로 선언하는 용도.</li>
 * </ul>
 * 기존 Drive 로그인 JWT(HS256)는 받지 않는다 — JWT인데 RS256이 아니면 즉시 거부.
 * 1st-party 로그인 세션은 /api 체인 전용이고, /mcp는 OAuth 연동·API Key로만 접근한다.
 *
 * 검증 실패 시 여기서 응답을 쓰지 않고 SecurityContext를 비운 채 체인을 계속
 * 진행한다 — 401 응답은 McpAuthenticationEntryPoint가 담당. 단 실패한 자격이
 * API Key(비 JWT)였다면 request attribute({@link McpConstants#AUTH_FAILURE_API_KEY_ATTR})로
 * 표시해, 엔트리포인트가 OAuth 디스커버리 트리거 없는 전용 401을 쓰게 한다.
 * HS256 거부는 JWT 형태 실패라 표시 없이 OAuth 온보딩 401(+ resource_metadata)로 떨어진다 —
 * "OAuth로 다시 연결하라"는 의미와 일치.
 *
 * scope 매핑 (Spring RS 표준 방식과 동일): 토큰의 scope 클레임을 SCOPE_ 프리픽스
 * authority로 변환해 Authentication에 싣는다. 시큐리티 체인·툴 검증은 이 authority를 본다.
 */
@Slf4j
@RequiredArgsConstructor
public class McpBearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService customUserDetailsService;
    private final OAuth2KeyManager oauth2KeyManager;
    private final OAuth2ClientConnectionService oauth2ClientConnectionService;
    private final ApiKeyFindService apiKeyFindService;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = (authorizationHeader != null && authorizationHeader.startsWith(DriveConstants.BEARER))
                ? authorizationHeader.substring(DriveConstants.BEARER.length())
                : null;

        try {
            if (token == null) {
                throw new AuthenticationException("Token Missing") {};
            }

            VerifiedToken verified = resolveToken(token, request);

            // API Key = 키 역할 / OAuth = 토큰 role 클레임(브리지가 정함, 지금은 USER) — 둘 다 역할 1개의 보유를 확인, 회수 시 401
            // (툴 단위 검사는 McpToolRoleAspect). ACTIVE 계정만 조회한다 — 없음·비활성·삭제는 여기서 UsernameNotFoundException(별도 활성 확인 없음).
            // 연결 확인보다 먼저 — 비활성 계정의 토큰으로 연결 last_used_date 가 기록되지 않게.
            UserDetails userDetails = customUserDetailsService.loadUserByUserSeq(verified.userSeq(), verified.role(), verified.authSource());

            // OAuth2 토큰(clientId 있음)은 연결 상태 확인 — 사용자가 연동 해제한 클라이언트의
            // 기발급 access 토큰을 잔여 TTL과 무관하게 즉시 차단한다. 같은 조회에서
            // last_used_date도 스로틀 기록 (MCP_OAUTH_CONNECTIONS_HANDOFF.md).
            // API Key는 clientId가 null — 연결 개념 없음, 검증·기록 제외.
            if (verified.clientId() != null
                    && !oauth2ClientConnectionService.verifyAndTouch(verified.userSeq(), verified.clientId())) {
                throw new BadCredentialsException("Revoked OAuth2 client connection: " + verified.clientId());
            }
            // 토큰 scope → SCOPE_ 프리픽스 authority 매핑 (배관).
            // McpSecurityConfig 체인 게이트(mcp:* 하나라도)와 쓰기 툴 McpScopeGuard(mcp:write)가 이 authority를 본다.
            List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());
            verified.scopes().forEach(scope ->
                    authorities.add(new SimpleGrantedAuthority(OAuth2Scope.AUTHORITY_PREFIX + scope)));

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("{ McpBearerTokenAuthenticationFilter } : 인증 성공 - userSeq : {}, scopes : {}",
                    verified.userSeq(), verified.scopes());

        } catch (AuthenticationException ex) {
            // API Key(비 JWT 불투명 문자열) 실패는 OAuth 재인증으로 해결되지 않는다(키 재발급 사안) —
            // 엔트리포인트가 디스커버리 트리거(resource_metadata) 없이 전용 응답을 쓰도록 표시한다.
            // 헤더 미존재·JWT 형태 실패는 표시 없이 기존 OAuth 온보딩 경로(401 + resource_metadata)로 남긴다.
            if (token != null && parseJwt(token) == null) {
                request.setAttribute(McpConstants.AUTH_FAILURE_API_KEY_ATTR, Boolean.TRUE);
            }
            log.info("{ McpBearerTokenAuthenticationFilter } : 인증 실패 - {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 토큰 형태로 검증 경로 선택 — 파싱은 여기서 한 번만 한다.
     * JWT(서명 JWS)로 파싱되면 RS256(OAuth2)만 허용, 파싱 자체가 안 되는
     * 불투명 문자열이면 API Key로 본다.
     * DB 조회보다 파싱이 싸므로 형태 판정을 먼저 한다 (결과는 "DB 우선 조회"와 동치 —
     * JWT가 tb_api_key에 있을 수 없고 API Key는 JWT로 파싱될 수 없다).
     * RS256이 아닌 JWT(기존 Drive 로그인 HS256 포함)는 /mcp에서 받지 않는다.
     */
    private VerifiedToken resolveToken(String token, HttpServletRequest request) {
        SignedJWT signedJwt = parseJwt(token);
        if (signedJwt == null) {
            return verifyApiKey(token);
        }
        if (!JWSAlgorithm.RS256.equals(signedJwt.getHeader().getAlgorithm())) {
            throw new BadCredentialsException("Unsupported token algorithm: only RS256 OAuth2 tokens are accepted on /mcp");
        }
        String baseUrl = McpUrls.baseUrl(request);
        return verifyOAuth2Token(signedJwt, baseUrl, baseUrl + McpConstants.MCP_ENDPOINT);
    }

    /** JWT(서명 JWS) 형태면 파싱 결과, 아니면 null — API Key(불투명 문자열) 판별 겸용. */
    private SignedJWT parseJwt(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * API Key(불투명 Base62) — MCP 설정 JSON 고정 헤더용.
     * 외부 API 체인(ApiKeyAuthenticationFilter)과 동일한 격리 조회를 재사용한다:
     * TenantFilter가 Host에서 주입한 tenantSeq와 함께 lookup(다른 테넌트 키 차단),
     * 조회 단계에서 status=ACTIVE·만료일(EXPIRE_DATE > NOW())까지 걸러진다.
     * <p>
     * scope는 키에 저장된 문자열을 role과 무관하게 그대로 반환한다 — 발급 시 부여한
     * scope(mcp:*)가 곧 실효 권한.
     * <p>
     * <b>주의</b> — 발급 시 모든 키가 mcp:* 를 함께 받도록 스펙이 바뀌어(읽기 전용 키도 mcp:read 포함),
     * 관리자·슈퍼관리자 키를 포함한 모든 API Key가 체인 게이트를 통과한다.
     * 예전에 admin 키를 막아주던 "scope가 null이라 빈 목록" 방어는 더 이상 없다.
     * role로 갈라야 하는 툴은 툴 본문에서 직접 검사할 것 — /mcp에는 경로 기반 role 검사가 없다.
     * <p>
     * clientId는 null — OAuth 연결(tb_oauth2_client_connection) 개념이 없으므로
     * 연결 상태 검증·기록을 건너뛴다.
     */
    private VerifiedToken verifyApiKey(String apiKey) {
        Integer tenantSeq = TenantContext.getOrThrow();

        ApiKeyData apiKeyData = apiKeyFindService.findActiveByApiKey(apiKey, tenantSeq);
        if (apiKeyData == null) {
            throw new BadCredentialsException("Invalid API Key");
        }

        apiKeyService.updateLastUsedDate(apiKey, apiKeyData.getLastUsedDate());

        return new VerifiedToken(apiKeyData.getUserSeq(), parseRawScopes(apiKeyData.getScope()), null, AuthSource.EXTERNAL, apiKeyData.getRoleType());
    }

    /** DB의 공백 구분 scope 문자열 → 목록. 값을 해석하지 않고 그대로 옮긴다 */
    private List<String> parseRawScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.trim().split("\\s+"));
    }

    /**
     * 토큰 검증 결과 — 사용자 식별 + 토큰에 부여된 scope (권한 매핑 재료)
     * + 발급 클라이언트 (연결 상태 검증 키. API Key는 null)
     * + 인증 채널의 AuthSource (외부 채널 차단 로직의 재료 — /mcp의 두 자격
     *   API Key·OAuth 클라이언트는 모두 EXTERNAL)
     * + 인증 역할 (API Key 는 키 역할, OAuth 는 토큰 role 클레임 — 항상 1개, null 아님).
     */
    private record VerifiedToken(int userSeq, List<String> scopes, String clientId, AuthSource authSource, RoleType role) {
    }

    /**
     * MCP OAuth 토큰(RS256, resolveToken에서 파싱·alg 확인 완료) — 헤더의 kid로 키를 선택해 서명·만료·audience 검증 후
     * subject(userSeq)·scope·role 반환.
     *
     * @param expectedIssuer   이 요청의 인가 서버 issuer(McpUrls.baseUrl) — 보호 리소스 메타데이터의 authorization_servers 와 같은 값
     * @param expectedResource 이 요청의 MCP 리소스 URI(McpUrls.baseUrl + /mcp) — 보호 리소스 메타데이터의 resource 와 같은 값
     */
    private VerifiedToken verifyOAuth2Token(SignedJWT signedJwt, String expectedIssuer, String expectedResource) {
        try {
            String kid = signedJwt.getHeader().getKeyID();
            if (kid == null) {
                throw new BadCredentialsException("MCP OAuth token has no kid");
            }

            RSAPublicKey publicKey = oauth2KeyManager.verificationKey(kid);
            if (publicKey == null) {
                throw new BadCredentialsException("Unknown MCP OAuth signing key: " + kid);
            }

            if (!signedJwt.verify(new RSASSAVerifier(publicKey))) {
                throw new BadCredentialsException("Invalid MCP OAuth token signature");
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.after(new Date())) {
                throw new BadCredentialsException("Expired MCP OAuth token");
            }

            String subject = claims.getSubject();
            if (subject == null) {
                throw new BadCredentialsException("MCP OAuth token has no user identifier");
            }
            int userSeq = Integer.parseInt(subject);

            // 이 테넌트(Host)의 issuer 가 발급한 토큰만 — 서명 키는 테넌트 공용이라 서명만으로는 발급 테넌트를 구분할 수 없다.
            // 다른 테넌트 토큰은 aud(리소스)·TenantBoundaryFilter 로도 막히지만 발급자 자체를 확인하는 심층 방어
            if (!expectedIssuer.equals(claims.getIssuer())) {
                throw new BadCredentialsException("MCP OAuth token issuer does not match this authorization server");
            }

            // RFC 8707 / MCP Token Audience Binding — 이 리소스(테넌트별 /mcp)를 대상으로 발급된 토큰만 받는다.
            // 커스터마이저가 aud 에 issuer + /mcp 를 고정으로 싣는다. 이 규칙 도입 전 발급분(aud = client_id 뿐)은
            // 여기서 401 → 클라이언트가 refresh 로 새 토큰을 받으면 통과한다(access TTL 이내 일시적).
            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(expectedResource)) {
                throw new BadCredentialsException("MCP OAuth token audience does not include this resource");
            }

            // 연결 상태 검증(tb_oauth2_client_connection)의 키 — client_id 클레임 (커스터마이저가 항상 싣는다)
            String clientId = claims.getStringClaim(OAuth2Constants.CLAIMS_CLIENT_ID);
            if (clientId == null) {
                throw new BadCredentialsException("MCP OAuth token has no client_id");
            }

            return new VerifiedToken(userSeq, extractScopes(claims), clientId, AuthSource.EXTERNAL, parseRole(claims));
        } catch (ParseException | JOSEException | NumberFormatException e) {
            throw new BadCredentialsException("Invalid MCP OAuth token", e);
        }
    }

    /**
     * role 클레임 → RoleType. 클레임 없는 토큰(role 클레임 도입 이전 발급·USER 고정 이전 인가 행의 refresh)은 USER,
     * 모르는 값은 거부 — null 로 흘려보내지 않는다. 값이 USER 인지는 여기서 다시 보지 않는다(정책은 브리지, 위조는 서명이 막음).
     */
    private RoleType parseRole(JWTClaimsSet claims) throws ParseException {
        String role = claims.getStringClaim(OAuth2Constants.CLAIMS_ROLE);
        if (role == null) {
            return RoleType.USER;
        }
        try {
            return RoleType.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Unknown role in MCP OAuth token: " + role);
        }
    }

    /**
     * scope 클레임 추출 — SAS JwtGenerator는 배열(JSON array)로 넣지만,
     * 방어적으로 RFC 8693식 공백 구분 문자열도 허용한다.
     */
    private List<String> extractScopes(JWTClaimsSet claims) {
        try {
            List<String> scopes = claims.getStringListClaim(OAuth2Constants.CLAIMS_SCOPE);
            if (scopes != null) {
                return scopes;
            }
        } catch (ParseException ignored) {
            // 배열이 아닌 형태 — 아래 문자열 경로로
        }
        Object raw = claims.getClaim(OAuth2Constants.CLAIMS_SCOPE);
        if (raw instanceof String value && !value.isBlank()) {
            return List.of(value.trim().split("\\s+"));
        }
        return List.of();
    }

}
