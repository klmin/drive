package com.thinkfree.drive.notification.payload.impl;

import com.thinkfree.drive.notification.payload.NotificationPayload;

public record FileUploadPayload
(
    String userName,
    String folderName,
    String fileName
)
    implements NotificationPayload
{

}
