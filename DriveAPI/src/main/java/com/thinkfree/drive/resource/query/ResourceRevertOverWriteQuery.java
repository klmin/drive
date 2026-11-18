package com.thinkfree.drive.resource.query;

import java.time.Instant;

public record ResourceRevertOverWriteQuery
(
    Integer userSeq,
    Long resourceSeq,
    Long restoreSizeByte,
    Integer restoreUpdateUserSeq,
    Instant restoreUpdateDate,
    Long currentSizeByte,
    Integer currentUpdateUserSeq,
    Instant currentUpdateDate
)
{
}
