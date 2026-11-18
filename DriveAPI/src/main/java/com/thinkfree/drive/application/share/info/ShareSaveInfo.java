package com.thinkfree.drive.application.share.info;

public record ShareSaveInfo
(
    Long shareSeq,
    Long resourceSeq,
    Integer userSeq,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canDelete,
    Boolean canShare
)
{
}
