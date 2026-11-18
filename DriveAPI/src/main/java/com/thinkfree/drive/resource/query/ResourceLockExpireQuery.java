package com.thinkfree.drive.resource.query;

import java.time.Instant;

public record ResourceLockExpireQuery
(
    Instant threshold,
    Integer updateUserSeq,
    Instant updateDate
)
{
}
