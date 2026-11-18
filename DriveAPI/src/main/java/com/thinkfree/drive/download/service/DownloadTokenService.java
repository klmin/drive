package com.thinkfree.drive.download.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.download.token.DownloadPayload;
import com.thinkfree.drive.download.token.MultiDownloadPayload;
import com.thinkfree.drive.download.token.VersionDownloadPayload;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class DownloadTokenService {

    @Value("${download.secret.key}")
    private String secretKey;

    @Value("${download.expire}")
    private Duration expire;

    private final ObjectMapper objectMapper;

    private Mac macTemplate; // thread-safe cloneable

    @PostConstruct
    void init() {
        try {
            macTemplate = Mac.getInstance("HmacSHA256");
            macTemplate.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize HMAC engine", e);
        }
    }

    /** 단기 다운로드용 signed URL token 생성 */
    public String createSignedUrl(Long resourceSeq, Integer userSeq, Integer sharedByUserSeq, int requestUserSeq) {
        long now = System.currentTimeMillis();
        long exp = now + (expire.toMillis());

        DownloadPayload payload = new DownloadPayload(
                resourceSeq,
                userSeq,
                sharedByUserSeq,
                requestUserSeq,
                exp
        );

        return createToken(payload);
    }

    public String createSignedUrlForMulti(LinkedHashSet<MultiDownloadPayload.DownloadItem> items,
                                          int requestUserSeq) {

        long now = System.currentTimeMillis();
        long exp = now + (expire.toMillis());

        MultiDownloadPayload payload = new MultiDownloadPayload(items, requestUserSeq, exp);

        return createToken(payload);
    }

    public String createSignedUrlForVersion(Long resourceSeq, Long resourceVersionSeq, Integer sharedByUserSeq, int requestUserSeq) {
        long now = System.currentTimeMillis();
        long exp = now + (expire.toMillis());

        VersionDownloadPayload payload = new VersionDownloadPayload(
                resourceSeq,
                resourceVersionSeq,
                sharedByUserSeq,
                requestUserSeq,
                exp
        );

        return createToken(payload);
    }

    private String createToken(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String signature = sign(payloadBase64);
            return payloadBase64 + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create download token", e);
        }
    }


    /** token 검증 + payload 반환 */
    public DownloadPayload validateAndParse(String token) {
        String json = extractJsonAfterVerify(token);

        try {
            DownloadPayload payload = objectMapper.readValue(json, DownloadPayload.class);
            validateExpiration(payload.expiresAt());
            return payload;

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid download token");
        }
    }

    public MultiDownloadPayload validateAndParseMulti(String token) {
        String json = extractJsonAfterVerify(token);

        try {
            MultiDownloadPayload payload = objectMapper.readValue(json, MultiDownloadPayload.class);
            validateExpiration(payload.expiresAt());
            return payload;

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid multi-download token");
        }
    }

    public VersionDownloadPayload validateAndParseVersion(String token) {
        String json = extractJsonAfterVerify(token);

        try {
            VersionDownloadPayload payload = objectMapper.readValue(json, VersionDownloadPayload.class);
            validateExpiration(payload.expiresAt());
            return payload;

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid version download token");
        }
    }

    /** 공통 만료 체크 */
    private void validateExpiration(long expiresAt) {
        if (expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Download token expired");
        }
    }

    /** 공통 토큰 파싱 & 서명 검증 */
    private String extractJsonAfterVerify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid download token format");
        }

        String payloadBase64 = parts[0];
        String signature = parts[1];

        if (!constantTimeEquals(signature, sign(payloadBase64))) {
            throw new IllegalArgumentException("Invalid download token signature");
        }

        try {
            return new String(
                    Base64.getUrlDecoder().decode(payloadBase64),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload");
        }
    }

    /** HMAC 서명 */
    private String sign(String payloadBase64) {
        try {
            Mac mac = (Mac) macTemplate.clone();
            byte[] raw = mac.doFinal(payloadBase64.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign payload", e);
        }
    }

    /** Constant time 비교 */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

}
