package com.thinkfree.drive.api.v1.admin.user.response;

import com.thinkfree.drive.user.enums.UserStatus;

public record AdminUserBulkStatusResponse(
        Integer userSeq,
        String userId,
        UserStatus status
) {
}
