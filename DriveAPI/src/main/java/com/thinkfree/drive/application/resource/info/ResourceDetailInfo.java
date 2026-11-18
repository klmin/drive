package com.thinkfree.drive.application.resource.info;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record ResourceDetailInfo
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
    Instant resourceRegisterDate,
    String resourceUpdateUserId,
    String resourceUpdateUserName,
    UserStatus resourceUpdateUserStatus,
    Instant resourceUpdateDate
)
{
}
