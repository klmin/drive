package com.thinkfree.drive.api.v1.admin.user.pendingdelete.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;

public record AdminPendingDeleteUserListResponse
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    List<RoleType> roles,
    @DriveDateTimeFormat
    Instant pendingDeleteDate,
    @DriveDateTimeFormat
    Instant deleteScheduleTime,
    Integer deleteUserSeq,
    String deleteUserId,
    UserStatus deleteUserStatus
)
{
}
