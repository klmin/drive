package com.thinkfree.drive.api.v1.resource.response;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ResourceTreeResponse
(
    Long resourceSeq,
    String resourceName,
    Long parentFolderSeq,
    ResourceType resourceType
)
{
}
