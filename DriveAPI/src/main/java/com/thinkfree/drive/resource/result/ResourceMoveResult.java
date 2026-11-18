package com.thinkfree.drive.resource.result;

import java.time.Instant;
import java.util.Collection;

public record ResourceMoveResult
(
    boolean userChanged,
    String fileName,
    long totalSizeByte,
    Collection<Long> resourceSeqList,
    Instant occurredDatetime
)
{
}
