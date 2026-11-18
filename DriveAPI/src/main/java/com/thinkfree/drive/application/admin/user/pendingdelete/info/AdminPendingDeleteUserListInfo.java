package com.thinkfree.drive.application.admin.user.pendingdelete.info;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * 삭제 대기 사용자 목록 항목 — 웹 응답(AdminPendingDeleteUserListResponse)과 필드 동형(웹 애노테이션 제외).
 * deleteUser*는 탈퇴 처리를 수행한 관리자 정보.
 */
public record AdminPendingDeleteUserListInfo
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    List<RoleType> roles,
    Instant pendingDeleteDate,
    Instant deleteScheduleTime,
    Integer deleteUserSeq,
    String deleteUserId,
    UserStatus deleteUserStatus
)
{
}
