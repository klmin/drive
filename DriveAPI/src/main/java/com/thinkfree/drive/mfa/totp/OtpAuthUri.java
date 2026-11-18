package com.thinkfree.drive.mfa.totp;

import dev.samstevens.totp.code.HashingAlgorithm;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record OtpAuthUri
(
    String issuer,
    String userId,
    String secret,
    HashingAlgorithm algorithm,
    int digits,
    long periodSeconds
)
{
    
    private static final String SCHEME = "otpauth";
    private static final String HOST_TOTP = "totp";
    
    public static OtpAuthUri totp(String issuer, String userId, String secret, HashingAlgorithm algorithm, int digits, long periodSeconds){
        
        requireText(issuer, "issuer");
        requireText(userId, "userId");
        requireText(secret, "secret");
        Objects.requireNonNull(algorithm, "algorithm");
        
        if (digits <= 0) {
            throw new IllegalArgumentException("digits must be positive");
        }
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("periodSeconds must be positive");
        }
        
        return new OtpAuthUri(issuer, userId, secret, algorithm, digits, periodSeconds);
    }
    
    public String toUriString() {
        String label = issuer + ":" + userId;
        return UriComponentsBuilder.newInstance()
                                   .scheme(SCHEME)
                                   .host(HOST_TOTP)
                                   .pathSegment(label)
                                   .queryParam("secret", secret)
                                   .queryParam("issuer", issuer)
                                   .queryParam("algorithm", algorithm.getFriendlyName())
                                   .queryParam("digits", digits)
                                   .queryParam("period", periodSeconds)
                                   .build()
                                   .encode(StandardCharsets.UTF_8)
                                   .toUriString();
    }
    
    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
    
}
