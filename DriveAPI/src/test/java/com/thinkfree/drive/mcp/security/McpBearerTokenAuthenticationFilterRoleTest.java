package com.thinkfree.drive.mcp.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.CustomUserDetailsService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.UserDetailDto;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.oauth2.connection.service.OAuth2ClientConnectionService;
import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.jwk.OAuth2KeyManager;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * /mcp 필터의 역할 — API Key 는 키 역할, OAuth 는 토큰 role 클레임(없으면 USER) 1개로 로드(보유 확인, 회수 시 인증 없음).
 * 계정 활성·연결 상태 확인은 통과로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpBearerTokenAuthenticationFilter — 키 역할·토큰 role 클레임")
class McpBearerTokenAuthenticationFilterRoleTest {

    private static final String KEY = "B6u6gFwHhyBum9Js_LRrcISG54dEC-HpZ45E4gtlvsQ";
    private static final String KID = "test-kid";
    private static final String CLIENT_ID = "client-1";
    private static final int TENANT = 1;
    private static final int USER_SEQ = 10;

    private static KeyPair keyPair;

    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private OAuth2KeyManager oauth2KeyManager;
    @Mock private OAuth2ClientConnectionService oauth2ClientConnectionService;
    @Mock private ApiKeyFindService apiKeyFindService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FilterChain chain;

    @InjectMocks
    private McpBearerTokenAuthenticationFilter filter;

    @BeforeAll
    static void rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(oauth2ClientConnectionService.verifyAndTouch(anyInt(), any())).willReturn(true);
        given(oauth2KeyManager.verificationKey(KID)).willReturn((RSAPublicKey) keyPair.getPublic());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static CustomUserDetails details(RoleType acting) {
        return new CustomUserDetails(new UserDetailDto(USER_SEQ, "u@test.com", "u", acting.getAuthority(), AuthSource.EXTERNAL, TENANT, null, Set.of(acting)));
    }

    private void apiKey(RoleType role) {
        given(apiKeyFindService.findActiveByApiKey(KEY, TENANT)).willReturn(new ApiKeyData(1, TENANT, USER_SEQ, "k", null, KEY,
                role, "mcp:read", ApiKeyStatus.ACTIVE, Instant.now().plusSeconds(600), null, 1, Instant.now(), false));
    }

    /** @param role role 클레임 값 — null 이면 클레임 없음(role 클레임 도입 이전 발급분) */
    private static String oauthToken(String subject, String role) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim(OAuth2Constants.CLAIMS_SCOPE, List.of("mcp:read"))
                .audience(CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 600_000));
        if (role != null) {
            builder.claim(OAuth2Constants.CLAIMS_ROLE, role);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), builder.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static String oauthToken() throws Exception {
        return oauthToken(String.valueOf(USER_SEQ), RoleType.USER.name());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain, atLeastOnce()).doFilter(any(), any());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("API Key — 키 역할 1개·EXTERNAL 로 로드, authority = 키 역할 + mcp scope")
    void apiKeyLoadsKeyRole() throws Exception {
        apiKey(RoleType.USER);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willReturn(details(RoleType.USER));

        Authentication auth = run(request(KEY));

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "SCOPE_mcp:read");
    }

    @Test
    @DisplayName("API Key — 키 역할 회수(로드 실패)면 인증 없음 + API Key 실패 표시(전용 401)")
    void apiKeyRevokedRole() throws Exception {
        apiKey(RoleType.ADMIN);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.ADMIN, AuthSource.EXTERNAL))
                .willThrow(new UsernameNotFoundException("x"));
        MockHttpServletRequest request = request(KEY);

        assertThat(run(request)).isNull();
        assertThat(request.getAttribute(McpConstants.AUTH_FAILURE_API_KEY_ATTR)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("OAuth — subject 의 userSeq·role 클레임 역할로 로드, authority = 그 역할 + mcp scope")
    void oauthLoadsClaimRole() throws Exception {
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willReturn(details(RoleType.USER));

        Authentication auth = run(request(oauthToken()));

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "SCOPE_mcp:read");
    }

    @Test
    @DisplayName("OAuth — role 클레임 없는 토큰(도입 이전 발급분)은 USER 로 로드")
    void oauthWithoutRoleClaimIsUser() throws Exception {
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willReturn(details(RoleType.USER));

        assertThat(run(request(oauthToken(String.valueOf(USER_SEQ), null)))).isNotNull();
    }

    @Test
    @DisplayName("OAuth — 모르는 role 값·subject 없음·숫자 아닌 subject 는 로드 없이 인증 없음")
    void oauthInvalidClaims() throws Exception {
        assertThat(run(request(oauthToken(String.valueOf(USER_SEQ), "ROOT")))).isNull();
        assertThat(run(request(oauthToken(null, RoleType.USER.name())))).isNull();
        assertThat(run(request(oauthToken("abc", RoleType.USER.name())))).isNull();
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class), any());
    }

    @Test
    @DisplayName("OAuth — 클레임 역할 회수(로드 실패)면 인증 없음")
    void oauthRoleRevoked() throws Exception {
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willThrow(new UsernameNotFoundException("x"));

        assertThat(run(request(oauthToken()))).isNull();
    }

    @Test
    @DisplayName("OAuth — 비활성·삭제 계정(로드 실패)이면 인증 없음, 연결 확인·last_used 기록 안 함")
    void oauthInactiveSkipsConnectionTouch() throws Exception {
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willThrow(new UsernameNotFoundException("x"));

        assertThat(run(request(oauthToken()))).isNull();
        verify(oauth2ClientConnectionService, never()).verifyAndTouch(anyInt(), any());
    }

    @Test
    @DisplayName("OAuth — 활성 계정이어도 연동 해제된 클라이언트면 인증 없음")
    void oauthRevokedConnection() throws Exception {
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER, AuthSource.EXTERNAL))
                .willReturn(details(RoleType.USER));
        given(oauth2ClientConnectionService.verifyAndTouch(USER_SEQ, CLIENT_ID)).willReturn(false);

        assertThat(run(request(oauthToken()))).isNull();
    }
}
