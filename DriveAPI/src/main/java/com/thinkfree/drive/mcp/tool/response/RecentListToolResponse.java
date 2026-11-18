package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 최근 문서함 API 명세와 동형 계약 — 항목 필드는 웹 응답(RecentListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * 날짜는 String — ToolDateTimeSupport 참고 (커서 값도 recentUpdateDate라 String으로 나간다).
 * webViewUrl만 MCP 전용 추가 필드 — 파일은 프런트 미리보기(/preview), 폴더는 폴더 열람(/drive|/shared) 딥링크.
 */
public record RecentListToolResponse
(
    List<RecentItem> resources,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record RecentItem(
            Long recentSeq,
            Integer userSeq,
            Long resourceSeq,
            Integer sharedByUserSeq,
            String ownerId,
            String ownerName,
            Integer ownerSeq,
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
            Boolean isStarred,
            Long starredSeq,
            Boolean isShared,
            Long shareSeq,
            Long shareResourceSeq,
            Long shareTargetSeq,
            Boolean canView,
            Boolean canEdit,
            Boolean canDownload,
            Boolean canUpload,
            Boolean canDelete,
            Boolean canShare,
            String resourceRegisterUserId,
            String resourceRegisterUserName,
            UserStatus resourceRegisterUserStatus,
            String resourceRegisterDate,
            String resourceUpdateUserId,
            String resourceUpdateUserName,
            UserStatus resourceUpdateUserStatus,
            String resourceUpdateDate,
            String recentRegisterDate,
            String recentUpdateDate,
            ResourceLocation resourceLocation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String webViewUrl
    )
    {
    }
}
