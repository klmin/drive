package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.user.enums.UserStatus;

public record ShareUserListResponse
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus userStatus,
    Integer tenantSeq,
    String tenantName
)
{
}
