package com.thinkfree.drive.api.v1.pinnedfolder.response;

import com.thinkfree.drive.resource.enums.ResourceType;

public record PinnedFolderListResponse
(
    Long pinnedFolderSeq,
    Long resourceSeq,
    Integer sharedByUserSeq,
    String resourceName,
    ResourceType resourceType
)
{
}
