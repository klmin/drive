package com.thinkfree.drive.notification.planner.impl;

import com.thinkfree.drive.notification.payload.impl.SharedPayload;
import com.thinkfree.drive.notification.planner.result.NotificationPlannerResult;
import com.thinkfree.drive.notification.planner.dto.NotificationPlannerDto;
import com.thinkfree.drive.notification.planner.NotificationPlanner;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.event.exception.NotificationEventErrorCode;
import com.thinkfree.drive.valid.DriveAssert;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SharedPlanner implements NotificationPlanner {
    @Override
    public ActionType supports() {
        return ActionType.SHARED;
    }
    
    @Override
    public NotificationPlannerResult plan(NotificationPlannerDto dto) {
        DriveAssert.throwIfAnyNull(NotificationEventErrorCode.REQUIRED.getMessage(), dto.getUserName(), dto.getFileName());
        var notificationPayload = new SharedPayload(dto.getUserName(), dto.getFileName(), dto.getTenantSeq(), dto.getTenantName());
        return new NotificationPlannerResult(notificationPayload, dto.getTargetUserSeqList());
    }
}
