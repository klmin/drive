package com.thinkfree.drive.share.result;

import java.time.Instant;
import java.util.Set;

public record ShareSaveResult
(
    Long shareSeq,
    Long resourceSeq,
    Integer userSeq,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canDelete,
    Boolean canShare,
    Set<Integer> addedUserSeqSet,
    Integer actorUserSeq,
    Instant occurredDatetime
)
{
}
