package com.thinkfree.drive.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.global.security.CustomUserDetailsService;
import com.thinkfree.drive.global.security.TenantBoundaryFilter;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.mcp.security.McpAuthenticationEntryPoint;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.oauth2.jwk.OAuth2KeyManager;
import com.thinkfree.drive.mcp.security.McpBearerTokenAuthenticationFilter;
import com.thinkfree.drive.mcp.security.McpRequestLoggingFilter;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * /mcp 엔드포인트 시큐리티 체인 — OAuth Resource Server 역할 (토큰 검증 쪽).
 * AS 역할(발급 쪽)의 체인 @Order(-2)/(-1)은 oauth2/config/OAuth2AuthorizationServerConfig에 있다.
 *
 * @Order는 앱 전역에서 조율된다: -2/-1(OAuth2 AS) → 0(여기) → 1/2(SecurityConfig의
 * /api/public, /api). 값이 겹치지 않게 SecurityConfig와 함께 봐야 한다.
 */
@Configuration
@RequiredArgsConstructor
public class McpSecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final OAuth2KeyManager oauth2KeyManager;
    private final OAuth2ClientConnectionService oauth2ClientConnectionService;
    private final ApiKeyFindService apiKeyFindService;
    private final ApiKeyService apiKeyService;
    private final McpAuthenticationEntryPoint mcpAuthenticationEntryPoint;
    private final ObjectMapper objectMapper;
    private final RoleHierarchy roleHierarchy;

    /**
     * MCP 엔드포인트 체인 — Bearer 토큰(MCP OAuth RS256 / API Key) 인증.
     * 기존 Drive 로그인 JWT(HS256)는 /mcp에서 받지 않는다.
     * 인증 실패 시 McpAuthenticationEntryPoint가 401 + WWW-Authenticate 헤더로
     * MCP 클라이언트의 OAuth 자동 발견을 트리거한다.
     * MCP 세션은 Mcp-Session-Id 헤더로 SDK가 관리하므로 서블릿 세션은 STATELESS.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
        McpBearerTokenAuthenticationFilter mcpBearerTokenAuthenticationFilter =
                new McpBearerTokenAuthenticationFilter(customUserDetailsService,
                        oauth2KeyManager, oauth2ClientConnectionService,
                        apiKeyFindService, apiKeyService);

        http.securityMatcher(McpConstants.MCP_ENDPOINT, McpConstants.MCP_ENDPOINT + "/**")
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(mcpAuthenticationEntryPoint)
            )
            // 스프링 기본 익명 인증 끔 — 익명 계정 역할 이름(ROLE_ANONYMOUS)과 같아 규칙에 ANONYMOUS 가 들어가면 무토큰이 통과한다.
            // 지금 규칙엔 없어 결과는 같다(인증 없음 → entry point 401), 내부 체인과 맞춰 둔다
            .anonymous(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authRequest -> authRequest
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    // 체인 게이트 = scope AND role 하한:
                    // ① mcp:* scope가 하나라도 있어야 통과(읽기 요구조건과 동치 — 쓰기⊇읽기 계약).
                    //    scope 조건을 빼면 mcp scope 없는 토큰(api:* 전용 키 등)이 scope 선언 없는
                    //    읽기 툴에 닿게 되므로 반드시 유지한다.
                    // ② USER·ADMIN(+ 하이어라키 SUPER_ADMIN > ADMIN) — "정식 계정 하한"이다(ANONYMOUS 차단). admin이 여기를 통과해도
                    //    툴별 정확 일치 검사(McpToolRoleAspect의 @McpToolRole)가 거부한다.
                    // 세부 read/write 구분은 쓰기 툴의 @McpToolScope(McpToolScopeAspect)가 담당한다.
                    .anyRequest().access(AuthorizationManagers.allOf(
                            AuthorityAuthorizationManager.hasAnyAuthority(
                                    OAuth2Scope.MCP_READ.authority(), OAuth2Scope.MCP_WRITE.authority()),
                            userRoleFloor()))
            )
            .addFilterBefore(new McpRequestLoggingFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mcpBearerTokenAuthenticationFilter, McpRequestLoggingFilter.class)
            // 유저 테넌트 == 요청 Host 테넌트 검증 — 다른 테넌트 서브도메인의 /mcp 호출 차단
            .addFilterAfter(new TenantBoundaryFilter(objectMapper), McpBearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 정식 계정 하한 매니저(USER·ADMIN, ANONYMOUS 차단) — 직접 생성한 AuthorityAuthorizationManager에는 전역
     * RoleHierarchy 빈이 자동 주입되지 않으므로(DSL hasRole과 달리) 명시적으로 꽂는다(SUPER_ADMIN > ADMIN 으로 SA 통과).
     * 하이어라키에 ADMIN > USER 가 없으므로 ADMIN 을 직접 나열한다. 툴별 배타 role 검사는 McpToolRoleAspect 몫이다.
     */
    private AuthorityAuthorizationManager<RequestAuthorizationContext> userRoleFloor() {
        AuthorityAuthorizationManager<RequestAuthorizationContext> manager =
                AuthorityAuthorizationManager.hasAnyAuthority(RoleType.USER.getAuthority(), RoleType.ADMIN.getAuthority());
        manager.setRoleHierarchy(roleHierarchy);
        return manager;
    }
}
