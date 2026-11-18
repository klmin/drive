package com.thinkfree.drive.api.v1.superadmin.apikey.response;

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
        /* 테넌트 무관 전체가 나오므로 소속 테넌트를 함께 내린다 */
        Integer tenantSeq,
        String tenantName,
        /* 키가 행세하는 대상 계정 */
        Integer userSeq,
        String userId,
        String userName,
        RoleType roleType,
        /* 키에 부여된 scope. 관리자·슈퍼관리자 키도 값을 갖지만 검사 대상은 아니다 */
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
        /* 보호 키 — 만료일·활성화·비활성화·재발급은 슈퍼관리자 경로에서만 */
        Boolean isProtected
) {
}
