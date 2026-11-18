package com.thinkfree.drive.access.dto;

import com.thinkfree.drive.user.enums.RoleType;

public record IpAccessCheckCommand
(
    String clientIp,
    RoleType roleType,
    int tenantSeq
)
{
}
