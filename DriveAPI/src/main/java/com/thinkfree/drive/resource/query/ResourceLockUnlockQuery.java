package com.thinkfree.drive.resource.query;

import java.time.Instant;

public record ResourceLockUnlockQuery(
        Long resourceSeq,
        Integer userSeq,
        Boolean isLocked,
        Instant lockedDate,
        Integer loginUserSeq,
        Instant updateDate
) {
}
