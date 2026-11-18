package com.thinkfree.drive.api.v1.starred.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record StarredListResponse
(
        Long starredSeq,
        Integer userSeq,
        Long resourceSeq,
        Integer sharedByUserSeq,
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
        Boolean isStarred,
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
        Instant starredRegisterDate,
        ResourceLocation resourceLocation
)
{
}
