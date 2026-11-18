package com.thinkfree.drive.api.v1.share.response;

public record ShareSaveResponse
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
