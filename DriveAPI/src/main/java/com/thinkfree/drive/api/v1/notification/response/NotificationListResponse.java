package com.thinkfree.drive.api.v1.notification.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.resource.enums.ResourceType;

import java.time.Instant;


public record NotificationListResponse
(
    Long notificationSeq,
    Integer sharedByUserSeq,
    ResourceType resourceType,
    Long resourceSeq,
    String resourceName,
    String ext,
    ActionType actionType,
    @JsonRawValue
    String description,
    Boolean isRead,
    Integer registerUserSeq,
    @DriveDateTimeFormat
    Instant registerDate
)
{
}
