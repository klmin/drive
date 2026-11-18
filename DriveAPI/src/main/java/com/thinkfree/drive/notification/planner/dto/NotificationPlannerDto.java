package com.thinkfree.drive.notification.planner.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationPlannerDto{
    private Integer sharedByUserSeq;
    private String userName;
    private String folderName;
    private String fileName;
    private Integer usagePercentage;
    private List<Integer> targetUserSeqList;
    private Integer tenantSeq;
    private String tenantName;
}

