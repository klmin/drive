package com.thinkfree.drive.notification.planner.result;

import com.thinkfree.drive.notification.payload.NotificationPayload;

import java.util.List;

public record NotificationPlannerResult
(
    NotificationPayload payload,
    List<Integer> recipients
)
{
}
