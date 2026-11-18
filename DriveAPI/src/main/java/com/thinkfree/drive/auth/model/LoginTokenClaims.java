package com.thinkfree.drive.auth.model;

import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.user.enums.RoleType;
import io.jsonwebtoken.Claims;

import java.time.Instant;

/**
 * 로그인 토큰(access·refresh — 발급은 {@code LoginTokenResult}) 클레임 — 서명 검증된 토큰을 한 번 파싱한 결과.
 * 같은 키로 서명하는 다른 토큰은 별도 객체({@code SsoStateClaims}·{@code SsoSignupClaims}) 또는 type 확인 뒤 userSeq 만(MFA).
 *
 * @param role 동작 역할(로그인 화면 역할). 클레임이 없거나(다중 role 이전 발급) 모르는 값이면 null
 */
public record LoginTokenClaims
(
    int userSeq,
    String sessionId,
    JwtType jwtType,
    RoleType role,
    Instant iat,
    Instant exp
)
{
    /**
     * @throws IllegalArgumentException 세션 토큰이 아님 — user_seq·iat·exp 가 없거나 type 을 모름(같은 키로 서명한 SSO·MFA 토큰 등)
     */
    public static LoginTokenClaims from(Claims claims){
        var userSeq = claims.get(JwtProvider.CLAIMS_USER_SEQ, Integer.class);
        if (userSeq == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Not a session token: user_seq/iat/exp missing");
        }
        var sessionId = claims.get(JwtProvider.CLAIMS_SESSION_ID, String.class);
        var jwtType = JwtType.findByName(claims.get(JwtProvider.CLAIMS_TYPE, String.class));
        var role = roleOf(claims.get(JwtProvider.CLAIMS_ROLE, String.class));
        var iat = claims.getIssuedAt().toInstant();
        var exp = claims.getExpiration().toInstant();
        return new LoginTokenClaims(userSeq, sessionId, jwtType, role, iat, exp);
    }

    public boolean isAccess() {
        return jwtType == JwtType.ACCESS;
    }

    public boolean isRefresh() {
        return jwtType == JwtType.REFRESH;
    }

    /** role 클레임 문자열 → RoleType, 없거나 모르는 값이면 null */
    public static RoleType roleOf(String role) {
        if (role == null) {
            return null;
        }
        try {
            return RoleType.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
