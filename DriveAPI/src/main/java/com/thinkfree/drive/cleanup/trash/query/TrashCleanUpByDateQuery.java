package com.thinkfree.drive.cleanup.trash.query;

import java.time.Instant;

public record TrashCleanUpByDateQuery(
        Instant thresholdDate,
        Boolean isDelete
) {
}
