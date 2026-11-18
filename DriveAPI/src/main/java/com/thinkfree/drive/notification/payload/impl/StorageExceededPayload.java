package com.thinkfree.drive.notification.payload.impl;

import com.thinkfree.drive.notification.payload.NotificationPayload;

public record StorageExceededPayload
(
    boolean exceeded
)
    implements NotificationPayload
{
}
