package com.thinkfree.drive.share.target.result;

import java.time.Instant;
import java.util.Set;

public record ShareTargetReplaceResult
(
    Integer sharedByUserSeq,
    Set<Integer> addedUserSeqSet,
    ShareTargetDeletionResult shareTargetDeletionResult,
    Instant occurredDatetime
)
{
}
