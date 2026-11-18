package com.thinkfree.drive.api.v1.recent.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record RecentListResponse
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
        @DriveDateTimeFormat
        Instant resourceRegisterDate,
        String resourceUpdateUserId,
        String resourceUpdateUserName,
        UserStatus resourceUpdateUserStatus,
        @DriveDateTimeFormat
        Instant resourceUpdateDate,
        @DriveDateTimeFormat
        Instant recentRegisterDate,
        @DriveDateTimeFormat
        Instant recentUpdateDate,
        ResourceLocation resourceLocation
)
{
}
