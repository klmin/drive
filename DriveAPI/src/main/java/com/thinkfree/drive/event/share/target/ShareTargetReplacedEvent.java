package com.thinkfree.drive.event.share.target;

import com.thinkfree.drive.resource.enums.ResourceType;

import java.util.List;
import java.util.Locale;

public record ShareTargetReplacedEvent
(
    Integer tenantSeq,
    String requestUserId,
    String requestUserName,
    ResourceType resourceType,
    String resourceName,
    List<String> shareTargetUserEmails,
    Locale locale
)
{
}
