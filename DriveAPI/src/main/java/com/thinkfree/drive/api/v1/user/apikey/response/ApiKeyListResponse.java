package com.thinkfree.drive.api.v1.user.apikey.response;

import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;

import java.time.Instant;
import java.util.List;

/** 본인이 발급한 키만 나오므로 대상 계정 정보는 담지 않는다 */
public record ApiKeyListResponse(
        Integer apiKeySeq,
        String keyName,
        String keyPurpose,
        String maskedApiKey,
        /* api:read / api:write */
        List<String> scopes,
        ApiKeyStatus status,
        @DriveDateTimeFormat
        Instant expireDate,
        /* 만료 키도 목록에 나온다 — 만료일을 연장하면 다시 쓸 수 있어서 */
        Boolean isExpired,
        @DriveDateTimeFormat
        Instant lastUsedDate,
        @DriveDateTimeFormat
        Instant registerDate,
        /* 보호 키 — 사용자 경로에서는 만료일·활성화·비활성화·재발급이 거부된다(API_KEY_PROTECTED) */
        Boolean isProtected
) {
}
