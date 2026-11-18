package com.thinkfree.drive.application.share.info;

import com.thinkfree.drive.user.enums.UserStatus;

import java.time.LocalDate;
import java.util.List;

public record ShareDetailInfo
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
