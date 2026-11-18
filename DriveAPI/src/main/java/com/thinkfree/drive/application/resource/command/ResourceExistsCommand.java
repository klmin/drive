package com.thinkfree.drive.application.resource.command;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ResourceExistsCommand
(
    Integer sharedByUserSeq,
    Long parentFolderSeq,
    String resourceName,
    ResourceType resourceType
)
{
}
