package com.thinkfree.drive.notification.command;

import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.event.entity.NotificationEvent;

import java.util.List;

public record EventWithNotifications
(
    NotificationEvent notificationEvent,
    List<Notification> notifications
)
{
}
