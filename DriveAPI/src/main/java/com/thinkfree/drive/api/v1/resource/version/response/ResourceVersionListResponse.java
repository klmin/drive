package com.thinkfree.drive.api.v1.resource.version.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record ResourceVersionListResponse
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
        @DriveDateTimeFormat
        Instant versionRegisterDate
)
{
}
