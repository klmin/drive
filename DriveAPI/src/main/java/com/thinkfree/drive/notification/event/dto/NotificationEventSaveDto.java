package com.thinkfree.drive.notification.event.dto;

import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.payload.NotificationPayload;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NotificationEventSaveDto {
    
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private ActionType actionType;
    private NotificationPayload payload;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
   
}
