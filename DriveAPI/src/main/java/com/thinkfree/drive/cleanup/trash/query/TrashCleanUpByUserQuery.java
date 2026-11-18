package com.thinkfree.drive.cleanup.trash.query;

public record TrashCleanUpByUserQuery(
        Integer userSeq,
        Boolean isDelete
) {
}
