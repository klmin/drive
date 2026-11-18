package com.thinkfree.drive.application.resource.info;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ResourceTreeInfo
(
    Long resourceSeq,
    String resourceName,
    Long parentFolderSeq,
    ResourceType resourceType
)
{
}
