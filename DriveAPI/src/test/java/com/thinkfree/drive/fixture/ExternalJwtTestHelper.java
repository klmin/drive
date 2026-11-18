package com.thinkfree.drive.fixture;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class ExternalJwtTestHelper {

    private ExternalJwtTestHelper() {}
    
    public static String create(String privateKeyBase64,
                                String issuer,
                                String subject,
                                String audience,
                                String kid,
                                Map<String, Object> extraClaims,
                                long expiresSeconds){
        RSAPrivateKey privateKey = decodePrivateKey(privateKeyBase64);
        return create(privateKey, issuer, subject, audience, kid, extraClaims, expiresSeconds);
    }
    
    public static String create(RSAPrivateKey privateKey,
                                String issuer,
                                String subject,
                                String audience,
                                String kid,
                                Map<String, Object> extraClaims,
                                long expiresSeconds) {
        
        Instant now = Instant.now();
        
        var builder = Jwts.builder()
                          .issuedAt(Date.from(now))
                          .expiration(Date.from(now.plusSeconds(expiresSeconds)))
                          .issuer(issuer)
                          .subject(subject)
                          .signWith(privateKey);
        
        if (audience != null){
            builder.audience().add(audience).and();
        }
        if(kid != null) {
            builder.header().add(JoseHeaderNames.KID, kid).and();
        }
        if (extraClaims != null){
            extraClaims.forEach(builder::claim);
        }

        return builder.compact();
    }
    
    public static Claims verify(String token, String publicKeyBase64) {
        RSAPublicKey publicKey = decodePublicKey(publicKeyBase64);
        return verify(token, publicKey);
    }
    
    public static Claims verify(String token, RSAPublicKey publicKey) {
        return Jwts.parser()
                   .verifyWith(publicKey)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
    }

    /** Base64 문자열 → RSAPrivateKey (PKCS#8) */
    public static RSAPrivateKey decodePrivateKey(String base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new RuntimeException("RSA 개인키 복원 실패", e);
        }
    }
    
    public static RSAPublicKey decodePublicKey(String base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new RuntimeException("RSA 공개키 복원 실패", e);
        }
    }
}
