package com.thinkfree.drive.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.access.service.IpAccessControlService;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.global.security.*;
import com.thinkfree.drive.global.security.properties.CorsProperty;
import com.thinkfree.drive.superadmin.properties.SuperAdminExternalProperty;
import com.thinkfree.drive.superadmin.security.SuperAdminIpGuard;
import com.thinkfree.drive.user.service.UserFindService;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final BlacklistTokenFindService blacklistTokenFindService;
    
    private final ApiKeyAuthenticationEntryPoint apiKeyAuthenticationEntryPoint;
    private final ApiKeyAccessDeniedHandler apiKeyAccessDeniedHandler;
    private final ApiKeyFindService apiKeyFindService;
    private final ApiKeyService apiKeyService;
    private final UserFindService userFindService;
    private final SuperAdminExternalProperty superAdminExternalProperty;
    private final ApiScopeAuthorizationManager apiScopeAuthorizationManager;

    private final SuperAdminIpGuard superAdminIpGuard;
    private final IpAccessControlService ipAccessControlService;
    private final AnonymousPathRegistry anonymousPathRegistry;
    private final ObjectMapper objectMapper;
    private final CorsProperty corsProperty;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(SecurityPaths.SYSTEM_PUBLIC);
    }

    /** 쿠키 인증 경로는 cors.allowed-origins 만, API Key·OAuth 토큰 경로는 모든 origin(credentials 없음) — {@link CorsConfigSourceFactory} */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return CorsConfigSourceFactory.create(corsProperty.allowedOrigins());
    }

    /**
     * ClientIpFilter가 차단 응답(403)을 반환할 때 CORS 헤더가 누락되는 이슈 해결 위해 추가
     * CORS 설정이 Spring Security 체인 내부에만 존재하면, ClientIpFilter(@Order -101)가
     * Security 체인보다 먼저 실행되어 차단 응답에 CORS 헤더가 붙지 않고 브라우저가 응답을 읽지 못함.
     *
     * @Bean + @Order 방식은 서블릿 필터 순서를 보장하지 않으므로,
     * FilterRegistrationBean을 사용해 HIGHEST_PRECEDENCE로 명시적 등록.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // @Order(0) /mcp 체인은 McpSecurityConfig(mcp/config)에 — MCP AS 체인(-2/-1)은 McpAuthorizationServerConfig에

    /** 외부 API scope 게이트 — api:* 하나라도(읽기 요구와 동치) */
    private static AuthorityAuthorizationManager<RequestAuthorizationContext> externalScopeGate() {
        return AuthorityAuthorizationManager.hasAnyAuthority(ApiKeyScope.READ.authority(), ApiKeyScope.WRITE.authority());
    }

    @Bean
    @Order(1)
    public SecurityFilterChain externalApiFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty);
        TenantBoundaryFilter tenantBoundaryFilter = new TenantBoundaryFilter(objectMapper);

        http.securityMatcher("/api/public/**")
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(apiKeyAuthenticationEntryPoint)
                    .accessDeniedHandler(apiKeyAccessDeniedHandler)
            )
            // 스프링 기본 익명 인증 끔 — 내부 체인과 같은 이유(익명 계정 역할 이름이 ROLE_ANONYMOUS).
            // 외부 규칙엔 ANONYMOUS 가 없어 결과는 같다(인증 없음 → entry point 401)
            .anonymous(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authRequest -> {
                    // role 경로 규칙은 ExternalApiRoleRules 한 곳에 있다 — 공개 OpenAPI 의 x-drive.roles 도 같은 표에서 나온다.
                    // 내부 체인의 role 경로 규칙과 동형. hasAnyRole("ADMIN") 은 RoleHierarchy(SUPER_ADMIN > ADMIN) 로 슈퍼관리자도 통과한다
                    ExternalApiRoleRules.RULES.forEach(rule ->
                            authRequest.requestMatchers(rule.pattern()).hasAnyRole(rule.roleArray()));
                    // scope 검사는 아래 기본 규칙에만 걸린다 — 위 role 규칙에 매칭된 관리자·슈퍼관리자 경로는 검사 대상이 아니다.
                    // 그쪽에 scope 대상이 생기면 해당 requestMatchers 에도 scope 검사를 추가해야 한다.
                    // 게이트(api:* 하나라도)가 읽기 요구를 겸하고(쓰기⊇읽기 계약),
                    // 쓰기 endpoint 의 @RequiredApiScope(WRITE) 만 apiScopeAuthorizationManager 가 따로 본다
                    // 외부 사용자 API 는 USER 키만(ADMIN 키 403)
                    authRequest.anyRequest().access(AuthorizationManagers.allOf(
                            AuthorityAuthorizationManager.hasAnyRole(ExternalApiRoleRules.DEFAULT_ROLES.toArray(String[]::new)),
                            externalScopeGate(),
                            apiScopeAuthorizationManager));
            })
            // 외부 API 체인:
            //   ApiKeyAuthenticationFilter  → 인증 + UserDetails 에 API key 의 tenantSeq 주입 (IP 정책은 적용하지 않음)
            //   TenantBoundaryFilter       → API key 의 tenantSeq == 요청 Host 의 tenantSeq 검증
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantBoundaryFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // 매 요청 JWT 인증 — 시큐리티가 세션을 만들거나 읽지 않는다
            // (세션 기반은 OAuth2 AS 체인(-2/-1)만 — authorize~코드발급 구간)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // API는 SavedRequest(로그인 후 복귀)를 안 쓰는데 기본 RequestCache는 401마다
            // 세션을 만든다 — spring-session-jdbc 도입으로 DB행이 되므로 차단
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            // 스프링 기본 익명 인증을 끈다 — 익명 계정 역할 이름(ROLE_ANONYMOUS)이 같아서, 켜 두면 토큰 없는 요청이
            // 아래 익명 허용 규칙을 통과한다. 끄면 인증 없는 요청은 규칙에 닿기 전에 401(entry point)
            .anonymous(AbstractHttpConfigurer::disable)
            // 동작 역할(토큰 role 1개) 기준. 하이어라키는 SUPER_ADMIN > ADMIN 만(RoleHierarchyConfig)
            // API Key 요청도 scope 검사하지 않는다(역할만) — scope 는 외부 체인에서만 검사
            .authorizeHttpRequests(authz -> authz
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers(SecurityPaths.AUTH_WHITELIST).permitAll()
                    .requestMatchers(SecurityPaths.SHARE_LINK_ACCESS).permitAll()
                    .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                    .requestMatchers("/api/v1/admins/**").hasRole("ADMIN")
                    .requestMatchers(SecurityPaths.COMMON_API_WITH_ANONYMOUS).hasAnyRole("USER", "ADMIN", "ANONYMOUS")
                    .requestMatchers(SecurityPaths.COMMON_API).hasAnyRole("USER", "ADMIN")
                    // 익명 계정은 @AnonymousAccessible 핸들러만(경로 + HTTP 메서드) — 거부는 JwtAccessDeniedHandler 가 REQUEST_006
                    .requestMatchers(anonymousPathRegistry::isAllowed).hasAnyRole("USER", "ANONYMOUS")
                    .anyRequest().hasRole("USER")
            )
            // 내부 체인: JWT(쿠키) → API Key(쿠키 인증이 없을 때만, SUPER_ADMIN 역할 키 제외) → IP → 테넌트 경계
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, customUserDetailsService, blacklistTokenFindService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(ApiKeyAuthenticationFilter.rejectingSuperAdminKey(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty), JwtAuthenticationFilter.class)
            .addFilterAfter(new IpAccessFilter(superAdminIpGuard, ipAccessControlService, objectMapper), ApiKeyAuthenticationFilter.class)
            .addFilterAfter(new TenantBoundaryFilter(objectMapper), IpAccessFilter.class);


        return http.build();
    }

}
