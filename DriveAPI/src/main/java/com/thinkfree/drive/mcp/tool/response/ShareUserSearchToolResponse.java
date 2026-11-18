package com.thinkfree.drive.mcp.tool.response;

import java.util.List;

/**
 * drive_search_share_users 툴 응답 — 웹 ShareUserSearchResponse(공유 후보 검색) 동형 계약.
 * 항목의 userSeq를 drive_share_resource / drive_update_share_targets의 targets[].userSeq로 그대로 쓴다.
 */
public record ShareUserSearchToolResponse
(
    List<ShareUserToolItem> users
)
{
    public record ShareUserToolItem
    (
        Integer userSeq,
        String userId,
        String userName,
        String userStatus,
        Integer tenantSeq,
        String tenantName
    )
    {
    }
}
