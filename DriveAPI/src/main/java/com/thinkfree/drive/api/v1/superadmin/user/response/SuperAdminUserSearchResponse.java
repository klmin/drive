package com.thinkfree.drive.api.v1.superadmin.user.response;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

public record SuperAdminUserSearchResponse(
        Integer userSeq,
        String userId,
        String userName,
        UserStatus status,
        List<RoleType> roles,
        Boolean isProtected
) {
}
