package com.thinkfree.drive.global.security;

import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.model.LoginTokenClaims;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.enums.RoleType;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 웹 JWT 필터 — {@code X-Drive-Role} 로 고른 쿠키(재발급 경로는 refresh, 그 외 access)에서 토큰을 읽고, 쿠키 역할과 role 클레임 일치를 본다.
 * 동작 역할은 토큰 role 클레임으로 {@code loadUserByUserSeq(seq, role)} 한 번(활성·역할 보유·로드).
 * 보유하면 인증(authority = 그 역할 1개), 못 찾으면(없음·비활성·회수) 인증 없음(401), 클레임 없음(다중 role 이전 토큰)은 DB 조회 없이 인증 없음.
 * 서명·만료·타입·블랙리스트는 기존 그대로라 통과로 고정한다(블랙리스트만 별도 케이스).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter — role 클레임 + 1회 조회")
class JwtAuthenticationFilterRoleTest {

    private static final String TOKEN = "token";
    private static final int USER_SEQ = 10;

    @Mock private JwtProvider jwtProvider;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private BlacklistTokenFindService blacklistTokenFindService;
    @Mock private FilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void validToken() {
        given(blacklistTokenFindService.existsByTokenHash(any())).willReturn(false);
    }

    /** {@code X-Drive-Role} 과 그 역할의 access·refresh 쿠키(값은 모두 TOKEN)를 실은 요청 */
    private static MockHttpServletRequest request(String method, String path, RoleType cookieRole) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(AuthCookieManager.ROLE_HEADER, cookieRole.name());
        request.setCookies(new Cookie(AuthCookieManager.accessCookieName(cookieRole), TOKEN),
                new Cookie(AuthCookieManager.refreshCookieName(cookieRole), TOKEN));
        return request;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 서명·만료 검증을 통과한 세션 토큰의 파싱 결과(파싱은 요청당 1회) */
    private void claims(JwtType type, RoleType role) {
        given(jwtProvider.parseLoginTokenClaims(TOKEN)).willReturn(
                new LoginTokenClaims(USER_SEQ, "sid", type, role, Instant.now(), Instant.now().plusSeconds(600)));
    }

    /** 관리자 화면(ADMIN 쿠키)의 일반 API 요청 */
    private Authentication run() throws Exception {
        return run(request("GET", "/api/v1/users", RoleType.ADMIN));
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain, atLeastOnce()).doFilter(any(), any());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("보유한 역할 — 인증, authority 는 그 역할 1개(ADMIN+USER 보유자라도 토큰 역할만)")
    void heldRoleAuthenticates() throws Exception {
        claims(JwtType.ACCESS, RoleType.ADMIN);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.ADMIN)).willReturn(new CustomUserDetails(
                new UserDetailDto(USER_SEQ, "u", "n", RoleType.ADMIN.getAuthority(), AuthSource.INTERNAL, 1, null, Set.of(RoleType.ADMIN))));

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(((CustomUserDetails) auth.getPrincipal()).getRoles()).containsExactly(RoleType.ADMIN);
    }

    @Test
    @DisplayName("계정 없음·비활성·삭제·역할 회수(조회 결과 없음 → UsernameNotFoundException) — 인증 없음, 체인은 계속")
    void notFoundRejected() throws Exception {
        claims(JwtType.ACCESS, RoleType.ADMIN);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.ADMIN)).willThrow(new UsernameNotFoundException("x"));

        assertThat(run()).isNull();
    }

    @Test
    @DisplayName("role 클레임 없는 토큰(다중 role 이전 발급)·모르는 역할 값 — 블랙리스트·계정 DB 조회 없이 인증 없음")
    void missingRoleClaimRejected() throws Exception {
        claims(JwtType.ACCESS, null);

        assertThat(run()).isNull();
        verify(blacklistTokenFindService, never()).existsByTokenHash(any());
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("블랙리스트(로그아웃) 토큰 — 역할 확인 전에 인증 없음(기존 동작 그대로)")
    void blacklistedRejectedBeforeRole() throws Exception {
        claims(JwtType.ACCESS, RoleType.ADMIN);
        given(blacklistTokenFindService.existsByTokenHash(any())).willReturn(true);

        assertThat(run()).isNull();
        verify(blacklistTokenFindService).existsByTokenHash(any());
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("서명·만료·형식 오류(JwtException) — 블랙리스트·DB 조회 없이 인증 없음(구 validateToken 실패와 같음)")
    void invalidTokenRejected() throws Exception {
        given(jwtProvider.parseLoginTokenClaims(TOKEN)).willThrow(new ExpiredJwtException(null, null, "expired"));

        assertThat(run()).isNull();
        verify(blacklistTokenFindService, never()).existsByTokenHash(any());
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("세션 토큰이 아님(같은 키로 서명한 SSO·MFA 토큰 — user_seq 없음·모르는 type, IllegalArgumentException) — 인증 없음(500 아님)")
    void nonSessionTokenRejected() throws Exception {
        given(jwtProvider.parseLoginTokenClaims(TOKEN)).willThrow(new IllegalArgumentException("Not a session token"));

        assertThat(run()).isNull();
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("ACCESS·REFRESH 외 타입(MFA 챌린지 등) — 인증 없음(구 isTokenType 실패와 같음)")
    void otherTypeRejected() throws Exception {
        claims(JwtType.MFA_CHALLENGE, RoleType.USER);

        assertThat(run()).isNull();
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("refresh 토큰은 POST /token/reissue 에서만 인증")
    void refreshOnlyOnReissue() throws Exception {
        claims(JwtType.REFRESH, RoleType.USER);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.USER)).willReturn(new CustomUserDetails(
                new UserDetailDto(USER_SEQ, "u", "n", RoleType.USER.getAuthority(), AuthSource.INTERNAL, 1, null, Set.of(RoleType.USER))));

        assertThat(run(request("POST", "/api/v1/token/reissue", RoleType.USER))).isNotNull();
    }

    @Test
    @DisplayName("refresh 토큰으로 다른 경로(GET /token/reissue 포함) — 블랙리스트·계정 조회 없이 인증 없음")
    void refreshRejectedElsewhere() throws Exception {
        claims(JwtType.REFRESH, RoleType.USER);

        assertThat(run(request("GET", "/api/v1/users", RoleType.USER))).isNull();
        SecurityContextHolder.clearContext();
        assertThat(run(request("GET", "/api/v1/token/reissue", RoleType.USER))).isNull();
        verify(blacklistTokenFindService, never()).existsByTokenHash(any());
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("재발급 경로에서 access 토큰 — 인증 없음(재발급은 refresh 만)")
    void accessRejectedOnReissue() throws Exception {
        claims(JwtType.ACCESS, RoleType.USER);

        assertThat(run(request("POST", "/api/v1/token/reissue", RoleType.USER))).isNull();
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("재발급 경로는 refresh 쿠키를, 그 외는 access 쿠키를 읽는다")
    void readsCookieByPath() throws Exception {
        MockHttpServletRequest reissue = new MockHttpServletRequest("POST", "/api/v1/token/reissue");
        reissue.addHeader(AuthCookieManager.ROLE_HEADER, "USER");
        reissue.setCookies(new Cookie("DRIVE_AT_USER", "access-token"), new Cookie("DRIVE_RT_USER", "refresh-token"));
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/v1/users");
        api.addHeader(AuthCookieManager.ROLE_HEADER, "USER");
        api.setCookies(new Cookie("DRIVE_AT_USER", "access-token"), new Cookie("DRIVE_RT_USER", "refresh-token"));
        given(jwtProvider.parseLoginTokenClaims(any())).willThrow(new IllegalArgumentException("x"));

        run(reissue);
        run(api);

        verify(jwtProvider).parseLoginTokenClaims("refresh-token");
        verify(jwtProvider).parseLoginTokenClaims("access-token");
    }

    @Test
    @DisplayName("쿠키 역할과 role 클레임이 다르면(사용자 쿠키에 관리자 토큰) — 블랙리스트·계정 조회 없이 인증 없음")
    void cookieRoleMismatchRejected() throws Exception {
        claims(JwtType.ACCESS, RoleType.ADMIN);

        assertThat(run(request("GET", "/api/v1/users", RoleType.USER))).isNull();
        verify(blacklistTokenFindService, never()).existsByTokenHash(any());
        verify(customUserDetailsService, never()).loadUserByUserSeq(any(), any(RoleType.class));
    }

    @Test
    @DisplayName("관리자 쿠키의 SUPER_ADMIN 토큰(슈퍼관리자의 테넌트 관리자 진입) — 인증, authority 는 SUPER_ADMIN")
    void superAdminTokenInAdminCookieAuthenticates() throws Exception {
        claims(JwtType.ACCESS, RoleType.SUPER_ADMIN);
        given(customUserDetailsService.loadUserByUserSeq(USER_SEQ, RoleType.SUPER_ADMIN)).willReturn(new CustomUserDetails(
                new UserDetailDto(USER_SEQ, "u", "n", RoleType.SUPER_ADMIN.getAuthority(), AuthSource.INTERNAL, null, null, Set.of(RoleType.SUPER_ADMIN))));

        Authentication auth = run(request("GET", "/api/v1/admins/users", RoleType.ADMIN));

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("X-Drive-Role 헤더 없음 — 쿠키가 있어도 파싱 없이 인증 없음")
    void missingRoleHeaderRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setCookies(new Cookie("DRIVE_AT_USER", TOKEN));

        assertThat(run(request)).isNull();
        verify(jwtProvider, never()).parseLoginTokenClaims(any());
    }

    @Test
    @DisplayName("헤더로 고른 역할의 쿠키가 없음(Authorization 헤더로 보낸 기존 방식 포함) — 파싱 없이 인증 없음")
    void missingCookieRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(AuthCookieManager.ROLE_HEADER, "USER");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        assertThat(run(request)).isNull();
        verify(jwtProvider, never()).parseLoginTokenClaims(any());
    }
}
