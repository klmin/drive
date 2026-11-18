package com.thinkfree.drive.application.version.info;

import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record VersionListInfo
(
    Long resourceVersionSeq,
    Integer userSeq,
    String userName,
    Integer tenantSeq,
    String tenantName,
    Long resourceSeq,
    Integer versionNumber,
    Long sizeByte,
    ResourceVersionType versionType,
    Integer ownerSeq,
    String ownerName,
    UserStatus ownerStatus,
    Integer ownerTenantSeq,
    String ownerTenantName,
    Instant versionRegisterDate
)
{
}
