package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 공유문서함 API 명세와 동형 계약 — 항목 필드는 웹 응답(SharedListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * 날짜는 String — ToolDateTimeSupport 참고 (SHARE/UPDATE/OPEN 정렬 시 커서 값도 날짜라 String으로 나간다).
 * webViewUrl만 MCP 전용 추가 필드 — 파일은 프런트 미리보기(/preview), 폴더는 폴더 열람(/shared) 딥링크.
 */
public record SharedListToolResponse
(
    List<SharedItem> resources,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record SharedItem(
            Long shareTargetSeq,
            Integer userSeq,
            Long shareSeq,
            Long resourceSeq,
            ResourceType resourceType,
            Long parentFolderSeq,
            String parentFolderName,
            String resourceName,
            String ext,
            Integer sharedByUserSeq,
            Integer ownerSeq,
            String ownerId,
            String ownerName,
            UserStatus ownerStatus,
            Integer ownerTenantSeq,
            String ownerTenantName,
            String fullPathSeq,
            String fullPath,
            Long sizeByte,
            Boolean isLocked,
            Boolean isStarred,
            Long starredSeq,
            String shareDate,
            Boolean canView,
            Boolean canEdit,
            Boolean canDownload,
            Boolean canUpload,
            Boolean canDelete,
            Boolean canShare,
            String shareTargetRegisterUserId,
            String shareTargetRegisterUserName,
            UserStatus shareTargetRegisterUserStatus,
            String shareTargetRegisterDate,
            String shareTargetUpdateUserId,
            String shareTargetUpdateUserName,
            UserStatus shareTargetUpdateUserStatus,
            String shareTargetUpdateDate,
            String resourceRegisterUserId,
            String resourceRegisterUserName,
            UserStatus resourceRegisterUserStatus,
            String resourceRegisterDate,
            String resourceUpdateUserId,
            String resourceUpdateUserName,
            UserStatus resourceUpdateUserStatus,
            String resourceUpdateDate,
            String recentUpdateDate,
            @JsonInclude(JsonInclude.Include.NON_NULL) String webViewUrl
    )
    {
    }
}
