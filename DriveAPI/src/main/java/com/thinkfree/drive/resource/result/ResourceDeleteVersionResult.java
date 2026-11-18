package com.thinkfree.drive.resource.result;

import java.time.Instant;

public record ResourceDeleteVersionResult
(
        String deleteResourceSaveName,
        Instant occurredDatetime
)
{
}
