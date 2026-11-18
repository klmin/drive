package com.thinkfree.drive.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.constants.DriveConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JwtUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static boolean isExpired(Long expEpochSeconds) {
        if (expEpochSeconds == null){
            return false;
        }
        return Instant.now().isAfter(Instant.ofEpochSecond(expEpochSeconds));
    }
    
    private static String stripBearer(String token) {
        return (token != null && token.startsWith(DriveConstants.BEARER)) ? token.substring(DriveConstants.BEARER.length()) : token;
    }
    
    public static String decodePayloadJson(String token) {
        String jwt = stripBearer(token);
        String[] parts = jwt.split(DriveConstants.TOKEN_SPLIT_SEPARATOR);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
    
    public static <T> T decodeAs(String token, Class<T> type) {
        try {
            return objectMapper.readValue(decodePayloadJson(token), type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode JWT payload to " + type.getSimpleName(), e);
        }
    }
    
    public static boolean shouldRefresh(Long expEpochSeconds, Duration threshold) {
        if (expEpochSeconds == null){
            return true;
        }
        Instant now = Instant.now();
        Instant exp = Instant.ofEpochSecond(expEpochSeconds);
        Duration remaining = Duration.between(now, exp);
        return remaining.compareTo(threshold) <= 0;
    }
    
}
