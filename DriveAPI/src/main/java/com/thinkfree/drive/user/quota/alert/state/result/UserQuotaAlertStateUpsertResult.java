package com.thinkfree.drive.user.quota.alert.state.result;

import java.time.Instant;

public record UserQuotaAlertStateUpsertResult
(
    int userSeq,
    int usagePercentage,
    int actorUserSeq,
    Instant occurredDatetime,
    boolean isHigherThan
)
{
}
