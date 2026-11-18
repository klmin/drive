package com.thinkfree.drive.notification.dto;

import com.thinkfree.drive.notification.event.enums.ActionType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class NotificationWithEventSaveDto {
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private ActionType actionType;
    private List<Integer> targetUserSeqList;
    
    private String userName;
    private String folderName;
    private String fileName;
    private Integer usagePercentage;
    
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Integer tenantSeq;
    private String tenantName;
}


