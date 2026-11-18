package com.thinkfree.drive.notification.planner.impl;

import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.event.exception.NotificationEventErrorCode;
import com.thinkfree.drive.notification.payload.impl.UnsharedPayload;
import com.thinkfree.drive.notification.planner.NotificationPlanner;
import com.thinkfree.drive.notification.planner.dto.NotificationPlannerDto;
import com.thinkfree.drive.notification.planner.result.NotificationPlannerResult;
import com.thinkfree.drive.valid.DriveAssert;
import org.springframework.stereotype.Component;

@Component
public class UnsharedPlanner implements NotificationPlanner {
    @Override
    public ActionType supports() {
        return ActionType.UNSHARED;
    }
    
    @Override
    public NotificationPlannerResult plan(NotificationPlannerDto dto) {
        DriveAssert.throwIfAnyNoText(NotificationEventErrorCode.REQUIRED.getMessage(), dto.getUserName(), dto.getFileName());
        var notificationPayload = new UnsharedPayload(dto.getUserName(), dto.getFileName(), dto.getTenantSeq(), dto.getTenantName());
        return new NotificationPlannerResult(notificationPayload, dto.getTargetUserSeqList());
    }
}
