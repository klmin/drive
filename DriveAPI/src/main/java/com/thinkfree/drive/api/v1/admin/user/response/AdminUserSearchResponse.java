package com.thinkfree.drive.api.v1.admin.user.response;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

public record AdminUserSearchResponse(
        Integer userSeq,
        String userId,
        String userName,
        UserStatus status,
        List<RoleType> roles,
        Boolean isProtected
) {
}
