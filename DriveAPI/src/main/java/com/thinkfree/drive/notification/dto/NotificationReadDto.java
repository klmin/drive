package com.thinkfree.drive.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class NotificationReadDto {
    private Long notificationSeq;
    private Integer userSeq;
    private Boolean isRead;
    private Instant readDatetime;
}
