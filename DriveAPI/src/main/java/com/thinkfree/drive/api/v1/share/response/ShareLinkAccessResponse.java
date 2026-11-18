package com.thinkfree.drive.api.v1.share.response;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ShareLinkAccessResponse
(
    Integer userSeq,
    Long resourceSeq,
    ResourceType resourceType,
    String redirectUrl,
    Boolean isPublicLink
)
{
}
