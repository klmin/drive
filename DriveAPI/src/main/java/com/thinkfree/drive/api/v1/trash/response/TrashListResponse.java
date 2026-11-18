package com.thinkfree.drive.api.v1.trash.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;

public record TrashListResponse
(
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
        @DriveDateTimeFormat
        Instant resourceRegisterDate,
        String resourceUpdateUserId,
        String resourceUpdateUserName,
        UserStatus resourceUpdateUserStatus,
        @DriveDateTimeFormat
        Instant resourceUpdateDate,
        Integer deleteUserSeq,
        String deleteUserId,
        String deleteUserName,
        Integer deleteUserTenantSeq,
        String deleteUserTenantName,
        UserStatus deleteUserStatus,
        @DriveDateTimeFormat
        Instant deleteDate
)
{
}
