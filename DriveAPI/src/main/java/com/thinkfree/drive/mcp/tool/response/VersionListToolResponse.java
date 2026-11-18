package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 버전 목록 API 명세와 동형 계약 — 항목 필드는 웹 응답(ResourceVersionListResponse) 전체와 동일하고,
 * 페이지 필드도 웹 page 봉투(ApiPage: totalCount/pageIndex/pageSize) 명명 그대로 평면 배치한다.
 * 봉투({data, error})만 MCP 규약. 날짜는 String — ToolDateTimeSupport 참고.
 */
public record VersionListToolResponse
(
    List<VersionItem> versions,
    long totalCount,
    int pageIndex,
    int pageSize
)
{
    public record VersionItem(
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
            String versionRegisterDate
    )
    {
    }
}
