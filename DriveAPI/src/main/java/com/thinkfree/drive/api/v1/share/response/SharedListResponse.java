package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record SharedListResponse
(
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
        @DriveDateTimeFormat
        Instant shareDate,
        Boolean canView,
        Boolean canEdit,
        Boolean canDownload,
        Boolean canUpload,
        Boolean canDelete,
        Boolean canShare,
        String shareTargetRegisterUserId,
        String shareTargetRegisterUserName,
        UserStatus shareTargetRegisterUserStatus,
        @DriveDateTimeFormat
        Instant shareTargetRegisterDate,
        String shareTargetUpdateUserId,
        String shareTargetUpdateUserName,
        UserStatus shareTargetUpdateUserStatus,
        @DriveDateTimeFormat
        Instant shareTargetUpdateDate,
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
        Instant recentUpdateDate
)
{
}
