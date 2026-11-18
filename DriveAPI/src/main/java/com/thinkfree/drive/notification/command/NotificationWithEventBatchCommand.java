package com.thinkfree.drive.notification.command;

import java.util.List;

public record NotificationWithEventBatchCommand
(
    List<EventWithNotifications> eventWithNotificationsList
)
{
}
