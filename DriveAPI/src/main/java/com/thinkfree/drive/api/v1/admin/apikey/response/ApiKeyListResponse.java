package com.thinkfree.drive.api.v1.admin.apikey.response;

import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.user.enums.RoleType;

import java.time.Instant;
import java.util.List;

public record ApiKeyListResponse(
        Integer apiKeySeq,
        String keyName,
        String keyPurpose,
        String maskedApiKey,
        /* 키가 행세하는 대상 계정 — 관리자가 사용자 대신 발급한 키가 섞여 있음 */
        Integer userSeq,
        String userId,
        String userName,
        RoleType roleType,
        /* 키에 부여된 scope. 관리자 키도 값을 갖지만 검사 대상은 아니다 */
        List<String> scopes,
        ApiKeyStatus status,
        @DriveDateTimeFormat
        Instant expireDate,
        /* 만료 키도 목록에 나온다 — 만료일을 연장하면 다시 쓸 수 있음 */
        Boolean isExpired,
        @DriveDateTimeFormat
        Instant lastUsedDate,
        @DriveDateTimeFormat
        Instant registerDate,
        /* 보호 키 — 슈퍼관리자가 보호 키로 지정한 키(발급 pt=true 또는 보호 설정 API)로, 관리자 경로에서는 만료일·활성화·비활성화·재발급이 거부된다(API_KEY_PROTECTED) */
        Boolean isProtected
) {
}
