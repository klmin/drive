package com.thinkfree.drive.mcp.tool.response;

/**
 * 내부 관리자 사용자 스토리지 사용량 API 명세와 동형 계약 — 필드는 웹 응답(AdminUserStorageUsageResponse) 및
 * application Info(AdminUserStorageUsageInfo)와 이름·타입 전부 동일하다. 봉투({data, error})만 MCP 규약.
 * 값은 모두 byte 단위.
 */
public record AdminUserStorageUsageToolResponse
(
    Long totalQuotaBytes,
    Long totalUsedBytes,
    Long liveUsedBytes,
    Long versionUsedBytes,
    Long trashUsedBytes
)
{
}
