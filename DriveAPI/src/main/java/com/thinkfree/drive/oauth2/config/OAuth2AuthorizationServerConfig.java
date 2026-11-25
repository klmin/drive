package com.thinkfree.drive.oauth2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.oauth2.OAuth2UserPrincipal;
import com.thinkfree.drive.oauth2.connection.service.ConnectionRecordingAuthorizationService;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.jwk.OAuth2KeyManager;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.List;

/**
 * OAuth2 인가 서버(AS) 설정 — Spring Authorization Server (MCP_OAUTH_FLOW.md §7).
 *
 * Drive 서버가 AS 역할(authorize/token/jwks/revoke + AS 메타데이터)을 겸임한다.
 * 현재 소비자(RS)는 MCP(/mcp) 하나 — RS 검증은 mcp 패키지의 mcpFilterChain
 * (McpBearerTokenAuthenticationFilter)이 담당한다. 서명 키는 OAuth2KeyManager의
 * RS256 키쌍을 공유 — AS가 서명하고 RS 필터가 같은 키로 검증한다.
 *
 * 체인 구성 (@Order 전역 조율: -2(여기) → 0(McpSecurityConfig) → 1/2(SecurityConfig)):
 * - @Order(-2) SAS 엔드포인트 체인 — 미인증 브라우저 진입 시 로그인 화면으로 유도.
 *   유도 대상은 C안 브리지의 프론트 라우트(OAuth2Constants.FRONT_LOGIN_PAGE — 상대경로라
 *   요청 Host의 테넌트 도메인 유지) — 프론트가 기존 로그인 후 POST /oauth2/session(브리지)으로
 *   세션을 확립한다 (MCP_OAUTH_BRIDGE_FLOW.md).
 *   로그인은 프론트(기존 local/OIDC/MFA 플로우)에서만 수행한다 — WAS 자체 폼로그인(A안) 체인은
 *   MFA 우회 여지가 있어 C안 검증 완료 후 제거됨.
 *
 * 세션: 이 체인만 기본 정책(IF_REQUIRED) — 로그인~코드발급 사이 운반 수단.
 * /mcp·/api/**는 기존대로 STATELESS 유지 (MCP_OAUTH_FLOW.md §3.1).
 *
 * 클라이언트/인가 상태 저장은 SAS 공식 스키마 기반 JDBC(oauth2_registered_client,
 * oauth2_authorization) — 재시작·멀티 WAS에서도 DCR 등록과 발급 토큰이 유지된다.
 * 테이블은 자동 생성되지 않으므로 설치 스크립트(initialize.sql)에 포함 (기동 시 fail-fast 검증).
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationServerConfig {

    private final OAuth2KeyManager oauth2KeyManager;

    @Bean
    @Order(-2)
    public SecurityFilterChain oauth2AuthorizationServerFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            .with(authorizationServerConfigurer, server -> server
                    // AS 메타데이터에 registration_endpoint 추가 — DCR은 SAS OIDC 모듈 대신
                    // 익명 허용 컨트롤러로 직접 구현했으므로 광고만 여기서 얹는다 (§2.4)
                    .authorizationServerMetadataEndpoint(metadata -> metadata
                            .authorizationServerMetadataCustomizer(builder -> builder
                                    .clientRegistrationEndpoint(AuthorizationServerContextHolder.getContext().getIssuer()
                                            + OAuth2Constants.REGISTRATION_PATH)))
                    // 커스텀 동의 화면 — 동의 필요 판정 시 SAS 내장 페이지 대신 프론트 라우트로
                    // 302 (client_id·scope·state 쿼리 동반). 프론트는 승인 결과를
                    // POST /oauth2/authorize (form: client_id, state, scope×N)로 제출한다.
                    .authorizationEndpoint(authorization -> authorization
                            .consentPage(OAuth2Constants.FRONT_CONSENT_PAGE))
            )
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
            // 미인증 브라우저가 /oauth2/authorize 진입 → 프론트 로그인 라우트로 (원 요청은 세션 RequestCache에 저장됨)
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(OAuth2Constants.FRONT_LOGIN_PAGE),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    /**
     * DCR로 등록된 클라이언트 저장소 — SAS 공식 스키마(oauth2_registered_client) 기반 JDBC.
     * CRUD SQL·RowMapper는 SAS 구현체 내장이라 스키마를 공식 DDL 그대로 써야 한다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
            verifyTableExists(jdbcTemplate, "oauth2_registered_client");
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * 인가 상태 저장소 — SAS 공식 스키마(oauth2_authorization) 기반 JDBC.
     * 인가 한 건의 전 생애주기(인가 코드 + PKCE challenge(attributes) + access/refresh 토큰)가
     * 한 행에 담긴다. 재시작·멀티 WAS에서도 코드 교환과 refresh가 이어지는 근거.
     *
     * attributes에는 로그인 Authentication이 Jackson 직렬화로 저장되므로, principal 타입
     * (OAuth2UserPrincipal)을 허용목록에 등록한 ObjectMapper를 읽기/쓰기 양쪽에 지정한다
     * — 기본 매퍼를 쓰면 "not in the allowlist" 예외로 토큰 교환이 실패한다.
     *
     * ConnectionRecordingAuthorizationService로 감싸 토큰 발급 시 연결 상태
     * (tb_oauth2_client_connection)를 upsert 한다 (MCP_OAUTH_CONNECTIONS_HANDOFF.md).
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository,
                                                           OAuth2ClientConnectionService connectionService) {
        verifyTableExists(jdbcTemplate, "oauth2_authorization");
        verifyTableExists(jdbcTemplate, "TB_OAUTH2_CLIENT_CONNECTION");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(JdbcOAuth2AuthorizationService.class.getClassLoader()));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(OAuth2UserPrincipal.class, OAuth2UserPrincipal.JacksonMixin.class);

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        rowMapper.setObjectMapper(objectMapper);
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parametersMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(objectMapper);

        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        authorizationService.setAuthorizationRowMapper(rowMapper);
        authorizationService.setAuthorizationParametersMapper(parametersMapper);
        return new ConnectionRecordingAuthorizationService(authorizationService, registeredClientRepository, connectionService);
    }

    /**
     * 동의(consent) 저장소 — SAS 공식 스키마(oauth2_authorization_consent) 기반 JDBC.
     * 사용자가 어떤 클라이언트에 어떤 scope를 승인했는지 (클라이언트, 사용자) 단위로 기록 —
     * 최초 인가 시 동의 화면(SAS 기본 내장 페이지)이 뜨고, 승인된 scope가 전부 커버되는
     * 재인가는 동의 없이 통과된다. 동의 요구 여부 자체는 클라이언트 행의
     * ClientSettings.requireAuthorizationConsent가 결정한다 (DCR 등록 정책 참조).
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        verifyTableExists(jdbcTemplate, "oauth2_authorization_consent");
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * SAS는 테이블을 자동 생성하지 않고 없으면 "첫 사용 시" 500으로 드러난다
     * — 설치 스크립트 누락을 기동 시점에 잡기 위한 fail-fast.
     * 존재 확인만 하면 되므로 COUNT(*) 풀스캔 대신 FETCH FIRST 1 ROWS ONLY (0행이어도 성공).
     */
    private static void verifyTableExists(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            jdbcTemplate.queryForList("SELECT 1 FROM " + tableName + " FETCH FIRST 1 ROWS ONLY");
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    tableName + " 테이블이 없습니다 — SAS 공식 DDL(spring-security-oauth2-authorization-server jar 내 *.sql) 실행 필요", e);
        }
    }

    /**
     * AS 서명 키 = OAuth2KeyManager 캐시의 "현재" 현역 키 (RS 필터도 같은 캐시로 검증).
     * 발급 때마다 호출되므로 rotate가 재기동 없이 다음 발급부터 반영된다.
     * 현역 키 1개만 공급 — 서명 가능한 키가 여럿이면 인코더가 모호성 에러를 낸다.
     * 따라서 /oauth2/jwks에는 현역 키 공개 부분만 노출되는데, RS=AS 한 몸이라 내부 검증은
     * 필터가 구 키까지 포함한 캐시로 하므로 문제 없다 (외부 RS가 생기면 그때 재검토).
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        return (jwkSelector, securityContext) -> jwkSelector.select(oauth2KeyManager.signingJwkSet());
    }

    /** 테넌트 서브도메인별 issuer — 요청 Host 기준으로 issuer가 결정된다 (§5.3). */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .multipleIssuersAllowed(true)
                .build();
    }

    /**
     * access 토큰 클레임 — subject = Drive userSeq, role = 브리지가 정한 동작 역할(principal 값 그대로 옮김, 여기서 정하지 않음).
     * RS 필터(McpBearerTokenAuthenticationFilter)의 RS256 경로가 subject 로 유저를 식별하고 role 보유를 DB 로 확인한다.
     * refresh 재발급도 저장된 인가 행의 principal 로 여기를 다시 거친다 — USER 고정 이전 행(역할 여러 개)은 role 을 넣지 않고,
     * 필터가 role 없는 토큰을 USER 로 본다.
     * 테넌트 경계 검증은 토큰 클레임이 아니라 TenantBoundaryFilter가 DB 로드 유저의
     * tenantSeq로 수행한다.
     *
     * aud = [client_id, MCP 리소스 URI] (RFC 8707 / MCP Authorization "Token Audience Binding") — 이 AS 는 /mcp 전용이라
     * 클라이언트가 보낸 resource 값을 복사하지 않고 서버가 정한 리소스 URI(issuer + /mcp)로 고정한다.
     * issuer 는 요청 Host 기준이라 테넌트마다 리소스 URI 가 달라지고, RS 필터는 같은 규칙(McpUrls.baseUrl + /mcp)으로 기대값을 만든다
     * (보호 리소스 메타데이터의 authorization_servers 가 issuer 와 같아야 OAuth 가 성립하므로 두 값은 일치한다).
     * client_id 는 aud 순서에 기대지 않도록 별도 클레임으로도 싣는다.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> oauth2TokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            String clientId = context.getRegisteredClient().getClientId();
            String resource = context.getAuthorizationServerContext().getIssuer() + McpConstants.MCP_ENDPOINT;
            context.getClaims()
                    .audience(List.of(clientId, resource))
                    .claim(OAuth2Constants.CLAIMS_CLIENT_ID, clientId);
            if (context.getPrincipal().getPrincipal() instanceof OAuth2UserPrincipal user) {
                context.getClaims().subject(String.valueOf(user.getUserSeq()));
                RoleType role = user.actingRole();
                if (role != null) {
                    context.getClaims().claim(OAuth2Constants.CLAIMS_ROLE, role.name());
                }
            }
        };
    }
}
