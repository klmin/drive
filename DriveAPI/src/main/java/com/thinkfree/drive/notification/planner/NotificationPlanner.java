package com.thinkfree.drive.notification.planner;

import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.planner.dto.NotificationPlannerDto;
import com.thinkfree.drive.notification.planner.result.NotificationPlannerResult;

public interface NotificationPlanner {
    ActionType supports();
    NotificationPlannerResult plan(NotificationPlannerDto command);
}
