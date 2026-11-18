package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.global.annotation.DriveDateFormat;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.LocalDate;
import java.util.List;

public record ShareDetailResponse
(
    Long shareSeq,
    Integer userSeq,
    Long resourceSeq,
    String shareType,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canDelete,
    Boolean canShare,
    String password,
    Boolean isPasswordEnabled,
    @DriveDateFormat
    LocalDate expireDate,
    Boolean modifiable,
    List<ShareTargetItem> targets
)
{
    public record ShareTargetItem
    (
        Long shareTargetSeq,
        Integer userSeq,
        String userId,
        String userName,
        UserStatus userStatus,
        Integer tenantSeq,
        String tenantName,
        Boolean canView,
        Boolean canEdit,
        Boolean canDownload,
        Boolean canUpload,
        Boolean canDelete,
        Boolean canShare
    )
    {
    }
}
