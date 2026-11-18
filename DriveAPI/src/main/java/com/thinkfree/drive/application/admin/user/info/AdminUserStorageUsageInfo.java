package com.thinkfree.drive.application.admin.user.info;

/** 사용자 스토리지 사용량 — 웹 응답(AdminUserStorageUsageResponse)과 필드 동형 */
public record AdminUserStorageUsageInfo
(
    Long totalQuotaBytes,
    Long totalUsedBytes,
    Long liveUsedBytes,
    Long versionUsedBytes,
    Long trashUsedBytes
)
{
}
