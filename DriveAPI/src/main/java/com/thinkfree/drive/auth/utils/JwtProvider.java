package com.thinkfree.drive.auth.utils;

import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.model.LoginTokenClaims;
import com.thinkfree.drive.auth.model.SsoSignupClaims;
import com.thinkfree.drive.auth.model.SsoStateClaims;
import com.thinkfree.drive.auth.properties.JwtProperty;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.user.enums.RoleType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class JwtProvider {
    
    public static final String CLAIMS_USER_SEQ = "user_seq";
    public static final String CLAIMS_TYPE = "type";
    public static final String CLAIMS_SESSION_ID = "sid";
    /** 동작 역할(access·refresh) — 로그인한 화면의 역할 1개, {@link RoleType#name()} */
    public static final String CLAIMS_ROLE = "role";
    public static final String CLAIMS_PROTOCOL = "protocol";
    public static final String CLAIMS_TENANT_SEQ = "tid";
    public static final String CLAIMS_SSO_PROVIDER_SEQ = "pid";
    public static final String CLAIMS_ERROR_CODE = "error_code";
    public static final String CLAIMS_ERROR_STATUS = "error_status";
    private static final Duration SSO_SESSION_TOKEN_TTL = Duration.ofMinutes(1);

    private final JwtProperty jwtProperty;
    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        secretKey = Keys.hmacShaKeyFor(jwtProperty.secret().key().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Integer userSeq, String sessionId, RoleType role) {
        return createTokenWithSession(userSeq, sessionId, role, jwtProperty.accessExp(), JwtType.ACCESS);
    }

    public String createRefreshToken(Integer userSeq, String sessionId, RoleType role) {
        return createTokenWithSession(userSeq, sessionId, role, jwtProperty.refreshExp(), JwtType.REFRESH);
    }

    public String createMfaChallengeToken(int userSeq, Duration ttl) {
        return createToken(userSeq, ttl, JwtType.MFA_CHALLENGE);
    }

    public String createMfaSetupChallengeToken(int userSeq, Duration ttl) {
        return createToken(userSeq, ttl, JwtType.MFA_SETUP_CHALLENGE);
    }

    public String createMfaConfirmChallengeToken(int userSeq, Duration ttl) {
        return createToken(userSeq, ttl, JwtType.MFA_CONFIRM_CHALLENGE);
    }

    private String createTokenWithSession(Integer userSeq, String sessionId, RoleType role, Duration exp, JwtType type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_USER_SEQ, userSeq);
        claims.put(CLAIMS_TYPE, type.getValue());
        claims.put(CLAIMS_SESSION_ID, sessionId);
        claims.put(CLAIMS_ROLE, role.name());
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                // jti: iat 가 초 단위라 같은 초에 재발급한 토큰이 기존과 동일해지는 것을 막는다(RTR 로 교체된 토큰은 항상 새 값)
                .id(UuidV7Generator.generate())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + exp.toMillis()))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private String createToken(Integer userSeq, Duration exp, JwtType type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_USER_SEQ, userSeq);
        claims.put(CLAIMS_TYPE, type.getValue());
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + exp.toMillis()))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
    
    public String createOidcSignupToken(int ssoProviderSeq, int tenantSeq, String subject, String email, String name){
        
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_SSO_PROVIDER_SEQ, ssoProviderSeq);
        claims.put(CLAIMS_TENANT_SEQ, tenantSeq);
        
        if (StringUtils.hasText(email)){
            claims.put(DriveConstants.EMAIL, email);
        }
        if (StringUtils.hasText(name)){
            claims.put(DriveConstants.NAME,  name);
        }
        
        return createSsoToken(claims, JwtType.SIGN_UP, subject, SsoProviderProtocolType.OIDC,  Duration.ofMinutes(15));
       
    }

    public int getId(String token) {
        return getClaims(token).get(CLAIMS_USER_SEQ, Integer.class);
    }

    public String getSessionId(String token) {
        return getClaims(token).get(CLAIMS_SESSION_ID, String.class);
    }

    /** 동작 역할 클레임 — 없거나(다중 role 이전 토큰) 모르는 값이면 null. 다른 클레임도 함께 필요하면 {@link #parseLoginTokenClaims} 로 한 번에 */
    public RoleType getRole(String token) {
        return LoginTokenClaims.roleOf(getClaims(token).get(CLAIMS_ROLE, String.class));
    }

    public String getTokenType(String token) {
        return getClaims(token).get(CLAIMS_TYPE, String.class);
    }

    public boolean isTokenType(String token, JwtType expectedType) {
        try {
            String type = getTokenType(token);
            return expectedType.getValue().equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            Date expiredDate = claims.getExpiration();
            Date now = new Date();
            return expiredDate.after(now);
        } catch (JwtException | IllegalArgumentException e) {
            return false; // 예외를 던지지 않고 false 반환
        }
    }

    private Jws<Claims> getJwsClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
    }
    
    public Claims getClaims(String token){
        return getJwsClaims(token).getPayload();
    }

    /**
     * 세션 토큰(access·refresh) 서명 검증 + 클레임 파싱 1회.
     * @throws io.jsonwebtoken.JwtException 서명·만료·형식 오류
     * @throws IllegalArgumentException 세션 토큰이 아님(user_seq·iat·exp 없음, 모르는 type)
     */
    public LoginTokenClaims parseLoginTokenClaims(String token) {
        Claims claims = getClaims(token);
        return LoginTokenClaims.from(claims);
    }

    public Optional<LoginTokenClaims> parseIfAccessToken(String token) {
        if (!StringUtils.hasText(token)){
            return Optional.empty();
        }
        try {
            LoginTokenClaims claims = LoginTokenClaims.from(getClaims(token));
            if (claims.jwtType() != JwtType.ACCESS){
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
    
    public int parseUserSeqAndValidate(String token, JwtType expectedType){
        Claims claims = getClaims(token);
        String type = claims.get(CLAIMS_TYPE, String.class);
        if (!expectedType.getValue().equals(type)) {
            throw new JwtException("Invalid token type: " + type);
        }
        return claims.get(CLAIMS_USER_SEQ, Integer.class);
    }
    
    public String createSsoStateToken(int ssoProviderSeq, int tenantSeq) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_SSO_PROVIDER_SEQ, ssoProviderSeq);
        claims.put(CLAIMS_TENANT_SEQ, tenantSeq);
        return createSsoToken(claims, JwtType.SSO_STATE, null, null, Duration.ofMinutes(5));
    }
    
    public SsoStateClaims validateSsoState(String state) {
        Claims claims = getClaims(state);
        if (!JwtType.SSO_STATE.getValue().equals(claims.get(CLAIMS_TYPE, String.class))) {
            throw new JwtException("SSO_STATE 토큰 타입이 아닙니다.");
        }
       
        Integer ssoProviderSeq = claims.get(CLAIMS_SSO_PROVIDER_SEQ, Integer.class);
        Integer tenantSeq = claims.get(CLAIMS_TENANT_SEQ, Integer.class);
        return new SsoStateClaims(ssoProviderSeq, tenantSeq);
    }
    
    public String createSsoLoginToken(int userSeq, int tenantSeq, SsoProviderProtocolType protocol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_USER_SEQ, userSeq);
        claims.put(CLAIMS_TENANT_SEQ, tenantSeq);
        return createSsoToken(claims, JwtType.SSO_LOGIN, null, protocol, SSO_SESSION_TOKEN_TTL);
    }
    
    public String createSsoMfaToken(int userSeq, int tenantSeq, SsoProviderProtocolType protocol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_USER_SEQ, userSeq);
        claims.put(CLAIMS_TENANT_SEQ, tenantSeq);
        return createSsoToken(claims, JwtType.SSO_MFA_VERIFY, null, protocol, SSO_SESSION_TOKEN_TTL);
    }

    public String createSsoMfaSetupToken(int userSeq, int tenantSeq, SsoProviderProtocolType protocol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_USER_SEQ, userSeq);
        claims.put(CLAIMS_TENANT_SEQ, tenantSeq);
        return createSsoToken(claims, JwtType.SSO_MFA_SETUP, null, protocol, SSO_SESSION_TOKEN_TTL);
    }
    
    public String createSsoErrorToken(String errorCode, int errorStatus, SsoProviderProtocolType protocol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIMS_ERROR_CODE, errorCode);
        claims.put(CLAIMS_ERROR_STATUS, errorStatus);
        return createSsoToken(claims, JwtType.SSO_ERROR, null, protocol, SSO_SESSION_TOKEN_TTL);
    }
    
    private String createSsoToken(Map<String, Object> claims, JwtType jwtType, String subject, SsoProviderProtocolType protocol, Duration duration) {
        
        claims.put(CLAIMS_TYPE, jwtType.getValue());
        
        if (protocol != null){
            claims.put(CLAIMS_PROTOCOL, protocol.name().toLowerCase());
        }
        
        Date now = new Date();
        
        var builder = Jwts.builder()
                          .claims(claims)
                          .issuedAt(now)
                          .expiration(new Date(now.getTime() + duration.toMillis()))
                          .signWith(secretKey, Jwts.SIG.HS256);
        
        if (StringUtils.hasText(subject)) {
            builder.subject(subject);
        }
        
        return builder.compact();
    }
    
    public SsoSignupClaims parseSsoSignupToken(String token) {
        Claims claims = getClaims(token);
        String claimType = claims.get(CLAIMS_TYPE, String.class);
        JwtType jwtType = JwtType.findByName(claimType);
        if (jwtType != JwtType.SIGN_UP) {
            throw new JwtException("SignUp 토큰 타입이 아닙니다.");
        }
        String protocol = claims.get(CLAIMS_PROTOCOL, String.class);
        SsoProviderProtocolType ssoProviderProtocolType = SsoProviderProtocolType.from(protocol);
        return new SsoSignupClaims(
                ssoProviderProtocolType,
                claims.get(CLAIMS_TENANT_SEQ, Integer.class),
                claims.get(CLAIMS_SSO_PROVIDER_SEQ, Integer.class),
                claims.getSubject(),
                claims.get(DriveConstants.EMAIL, String.class),
                claims.get(DriveConstants.NAME, String.class)
        );
    }
    

}
