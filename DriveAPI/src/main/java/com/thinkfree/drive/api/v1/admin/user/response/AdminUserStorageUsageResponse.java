package com.thinkfree.drive.api.v1.admin.user.response;

public record AdminUserStorageUsageResponse
(
    Long totalQuotaBytes,
    Long totalUsedBytes,
    Long liveUsedBytes,
    Long versionUsedBytes,
    Long trashUsedBytes
)
{
}
