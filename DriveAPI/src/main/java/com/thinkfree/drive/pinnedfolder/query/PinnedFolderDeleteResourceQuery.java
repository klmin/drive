package com.thinkfree.drive.pinnedfolder.query;

public record PinnedFolderDeleteResourceQuery
(
    Integer userSeq,
    int sharedByUserSeq,
    long resourceSeq
)
{
}
