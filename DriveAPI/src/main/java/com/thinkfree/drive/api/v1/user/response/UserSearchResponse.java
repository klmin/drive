package com.thinkfree.drive.api.v1.user.response;

import com.thinkfree.drive.user.enums.UserStatus;

public record UserSearchResponse
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    Integer tenantSeq,
    String tenantName
)
{
}
