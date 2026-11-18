package com.thinkfree.drive.application.recent.info;

import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record RecentListInfo
(
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
    Instant resourceRegisterDate,
    String resourceUpdateUserId,
    String resourceUpdateUserName,
    UserStatus resourceUpdateUserStatus,
    Instant resourceUpdateDate,
    Instant recentRegisterDate,
    Instant recentUpdateDate,
    ResourceLocation resourceLocation
)
{
}
