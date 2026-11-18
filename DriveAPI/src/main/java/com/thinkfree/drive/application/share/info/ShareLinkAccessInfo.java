package com.thinkfree.drive.application.share.info;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ShareLinkAccessInfo
(
    Integer userSeq,
    Long resourceSeq,
    ResourceType resourceType,
    String redirectUrl,
    Boolean isPublicLink
)
{
}
