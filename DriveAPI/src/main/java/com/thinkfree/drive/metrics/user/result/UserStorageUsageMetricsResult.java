package com.thinkfree.drive.metrics.user.result;

public record UserStorageUsageMetricsResult
(
        Long totalQuotaBytes,
        Long totalUsedBytes,
        Long liveUsedBytes,
        Long versionUsedBytes,
        Long trashUsedBytes
)
{
}
