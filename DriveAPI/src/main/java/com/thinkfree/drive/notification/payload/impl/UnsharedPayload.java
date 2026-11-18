package com.thinkfree.drive.notification.payload.impl;

import com.thinkfree.drive.notification.payload.NotificationPayload;

public record UnsharedPayload
(
    String userName,
    String fileName,
    Integer tenantSeq,
    String tenantName
)
    implements NotificationPayload
{
}
