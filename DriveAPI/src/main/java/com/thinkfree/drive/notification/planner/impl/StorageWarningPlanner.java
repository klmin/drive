package com.thinkfree.drive.notification.planner.impl;

import com.thinkfree.drive.notification.payload.impl.StorageWarningPayload;
import com.thinkfree.drive.notification.planner.result.NotificationPlannerResult;
import com.thinkfree.drive.notification.planner.dto.NotificationPlannerDto;
import com.thinkfree.drive.notification.planner.NotificationPlanner;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.event.exception.NotificationEventErrorCode;
import com.thinkfree.drive.valid.DriveAssert;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageWarningPlanner implements NotificationPlanner {
    @Override
    public ActionType supports() {
        return ActionType.STORAGE_WARNING;
    }
    
    @Override
    public NotificationPlannerResult plan(NotificationPlannerDto dto) {
        DriveAssert.throwIfNull(dto.getUsagePercentage(), NotificationEventErrorCode.REQUIRED.getMessage());
        var notificationPayload = new StorageWarningPayload(dto.getUsagePercentage());
        return new NotificationPlannerResult(notificationPayload, List.of(dto.getSharedByUserSeq()));
    }
}
