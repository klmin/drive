package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

public record ShareUserSearchResponse(
    List<UserSearchItem> items
) {
    public record UserSearchItem(
        Integer userSeq,
        String userId,
        String userName,
        UserStatus userStatus,
        Integer tenantSeq,
        String tenantName
    ) {}
}
