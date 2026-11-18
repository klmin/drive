package com.thinkfree.drive.notification.dto;

import java.time.Instant;

public record NotificationQuotaExceededDto
(
    Integer userSeq,
    Integer actorUserSeq,
    Instant occurredDatetime
)
{
}
