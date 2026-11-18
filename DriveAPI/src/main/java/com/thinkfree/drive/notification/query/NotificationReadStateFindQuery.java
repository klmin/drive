package com.thinkfree.drive.notification.query;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationReadStateFindQuery {
    private Integer userSeq;
    private Boolean isRead;
}
