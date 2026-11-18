package com.thinkfree.drive.pinnedfolder.query;

import java.util.List;

public record PinnedFolderDeleteUserResourcesQuery
(
    Integer userSeq,
    Integer sharedByUserSeq,
    List<Long> resourceSeqList
)
{
}
