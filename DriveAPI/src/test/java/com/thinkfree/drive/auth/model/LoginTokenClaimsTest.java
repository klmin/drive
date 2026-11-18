package com.thinkfree.drive.auth.model;

import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.properties.JwtProperty;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.user.enums.RoleType;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 세션 토큰 1회 파싱({@link JwtProvider#parseLoginTokenClaims}) — 필터·재발급이 쓰는 값(userSeq·sessionId·type·role)과
 * 실패 분류(서명·만료 = JwtException, 세션 토큰 아님 = IllegalArgumentException). 실제 서명 키로 만든 토큰(스프링 없이).
 */
@DisplayName("LoginTokenClaims — 세션 토큰 파싱 1회")
class LoginTokenClaimsTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(new JwtProperty(new JwtProperty.Secret(SECRET), Duration.ofMinutes(10), Duration.ofDays(1)));
        ReflectionTestUtils.invokeMethod(jwtProvider, "init");
    }

    private String sign(Map<String, Object> claims, Date exp) {
        return Jwts.builder().claims(claims).issuedAt(new Date()).expiration(exp)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    @Test
    @DisplayName("access·refresh — userSeq·sessionId·type·role 한 번에, 발급 시각·만료 시각")
    void parsesSessionToken() {
        LoginTokenClaims access = jwtProvider.parseLoginTokenClaims(jwtProvider.createAccessToken(7, "sid-1", RoleType.ADMIN));
        LoginTokenClaims refresh = jwtProvider.parseLoginTokenClaims(jwtProvider.createRefreshToken(7, "sid-1", RoleType.USER));

        assertThat(access.userSeq()).isEqualTo(7);
        assertThat(access.sessionId()).isEqualTo("sid-1");
        assertThat(access.jwtType()).isEqualTo(JwtType.ACCESS);
        assertThat(access.role()).isEqualTo(RoleType.ADMIN);
        assertThat(access.exp()).isAfter(access.iat());
        assertThat(refresh.jwtType()).isEqualTo(JwtType.REFRESH);
        assertThat(refresh.role()).isEqualTo(RoleType.USER);
        assertThat(access.isAccess()).isTrue();
        assertThat(access.isRefresh()).isFalse();
        assertThat(refresh.isRefresh()).isTrue();
        assertThat(refresh.isAccess()).isFalse();
    }

    @Test
    @DisplayName("role 클레임 없음(다중 role 이전)·모르는 값 — role null, 나머지는 그대로 파싱")
    void missingOrUnknownRole() {
        Date exp = new Date(System.currentTimeMillis() + 600_000);
        LoginTokenClaims legacy = jwtProvider.parseLoginTokenClaims(sign(Map.of("user_seq", 7, "type", "access", "sid", "s"), exp));
        LoginTokenClaims unknown = jwtProvider.parseLoginTokenClaims(sign(Map.of("user_seq", 7, "type", "access", "sid", "s", "role", "HACKER"), exp));

        assertThat(legacy.role()).isNull();
        assertThat(legacy.userSeq()).isEqualTo(7);
        assertThat(unknown.role()).isNull();
        assertThat(jwtProvider.getRole(sign(Map.of("user_seq", 7, "type", "access", "role", "USER"), exp))).isEqualTo(RoleType.USER);
    }

    @Test
    @DisplayName("세션 토큰이 아님(같은 키로 서명) — SSO 상태 토큰(user_seq 없음)·모르는 type 은 IllegalArgumentException(NPE 아님)")
    void nonSessionTokens() {
        String ssoState = jwtProvider.createSsoStateToken(1, 1);
        String unknownType = sign(Map.of("user_seq", 7, "type", "nope", "role", "USER"), new Date(System.currentTimeMillis() + 600_000));

        assertThatThrownBy(() -> jwtProvider.parseLoginTokenClaims(ssoState)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtProvider.parseLoginTokenClaims(unknownType)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jwtProvider.parseLoginTokenClaims(jwtProvider.createMfaChallengeToken(7, Duration.ofMinutes(1))).jwtType())
                .as("MFA 챌린지는 세션 토큰 형식이라 파싱은 되고 type 으로 거른다").isEqualTo(JwtType.MFA_CHALLENGE);
    }

    @Test
    @DisplayName("만료·다른 키 서명·형식 오류 — JwtException")
    void invalidTokens() {
        String expired = sign(Map.of("user_seq", 7, "type", "access", "role", "USER"), new Date(System.currentTimeMillis() - 1_000));
        String otherKey = Jwts.builder().claims(Map.of("user_seq", 7, "type", "access", "role", "USER")).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> jwtProvider.parseLoginTokenClaims(expired)).isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> jwtProvider.parseLoginTokenClaims(otherKey)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtProvider.parseLoginTokenClaims("not.a.jwt")).isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }
}
