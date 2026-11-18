package com.thinkfree.drive.event.file;

import java.time.Instant;

public record FileUploadedEvent
(
    Integer userSeq,
    Integer actorUserSeq,
    Instant occurredDatetime
)
{
}
