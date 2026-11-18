package com.thinkfree.drive.application.admin.user.info;

import com.thinkfree.drive.user.enums.UserStatus;

/** 사용자 상태 일괄 조회 항목 — 웹 응답(AdminUserBulkStatusResponse)과 필드 동형 */
public record AdminUserBulkStatusInfo
(
    Integer userSeq,
    String userId,
    UserStatus status
)
{
}
