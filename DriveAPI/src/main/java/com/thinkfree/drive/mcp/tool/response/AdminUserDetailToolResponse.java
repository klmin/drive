package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * drive_admin_get_user 응답 — 웹 GET /api/v1/admins/users/{userSeq}(AdminResponse)와 필드 동형 (roles = 보유 역할, list_users 툴과 일관).
 * 의도된 차이는 날짜 포맷 하나: 전 툴 공통 String(ToolDateTimeSupport) — 웹의 "yyyy. M. d. HH:mm"와 다름.
 */
public record AdminUserDetailToolResponse
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    Long totalQuotaByte,
    Long useQuotaByte,
    List<RoleType> roles,
    Integer registerUserSeq,
    String registerDate,
    Integer updateUserSeq,
    String updateDate,
    String countryCode,
    String phoneNumber,
    String profileImageKey,
    Boolean isMfaEnabled,
    Boolean isProtected
)
{
}
