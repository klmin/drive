package com.thinkfree.drive.api.v1.resource.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record ResourceDetailResponse
(
        Long resourceSeq,
        ResourceType resourceType,
        Long parentFolderSeq,
        String parentFolderName,
        String resourceName,
        String ext,
        Integer userSeq,
        Integer ownerSeq,
        UserStatus ownerStatus,
        String ownerId,
        String ownerName,
        Integer ownerTenantSeq,
        String ownerTenantName,
        String fullPathSeq,
        String fullPath,
        Long sizeByte,
        Boolean isLocked,
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
        Instant resourceUpdateDate
)
{
}
