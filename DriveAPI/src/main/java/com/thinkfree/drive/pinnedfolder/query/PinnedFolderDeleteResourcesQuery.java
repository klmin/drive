package com.thinkfree.drive.pinnedfolder.query;

import java.util.List;

public record PinnedFolderDeleteResourcesQuery
(
    Integer userSeq,
    int sharedByUserSeq,
    List<Long> resourceSeqList,
    List<Integer> excludeUserSeqList,
    Integer tenantSeq
)
{
}
