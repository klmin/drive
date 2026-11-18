package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 휴지통 API 명세와 동형 계약 — 항목 필드는 웹 응답(TrashListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * 날짜는 String — ToolDateTimeSupport 참고 (REGISTER(삭제일) 정렬 시 커서 값도 날짜라 String으로 나간다).
 */
public record TrashListToolResponse
(
    List<TrashItem> resources,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record TrashItem(
            Long trashSeq,
            Long resourceSeq,
            Integer userSeq,
            Integer ownerSeq,
            String ownerId,
            String ownerName,
            UserStatus ownerStatus,
            Integer ownerTenantSeq,
            String ownerTenantName,
            ResourceType resourceType,
            Long parentFolderSeq,
            String parentFolderName,
            String resourceName,
            String ext,
            String fullPathSeq,
            String fullPath,
            Long sizeByte,
            Boolean isLocked,
            String resourceRegisterUserId,
            String resourceRegisterUserName,
            UserStatus resourceRegisterUserStatus,
            String resourceRegisterDate,
            String resourceUpdateUserId,
            String resourceUpdateUserName,
            UserStatus resourceUpdateUserStatus,
            String resourceUpdateDate,
            Integer deleteUserSeq,
            String deleteUserId,
            String deleteUserName,
            Integer deleteUserTenantSeq,
            String deleteUserTenantName,
            UserStatus deleteUserStatus,
            String deleteDate
    )
    {
    }
}
