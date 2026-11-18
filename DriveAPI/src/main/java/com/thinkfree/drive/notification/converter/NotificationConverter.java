package com.thinkfree.drive.notification.converter;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.planner.dto.NotificationPlannerDto;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationConverter {
    
    NotificationPlannerDto toNotificationPlannerDto(NotificationWithEventSaveDto dto);
    
    @Mapping(target = "isRead", constant = "false")
    Notification toSaveNotification(Integer userSeq, Long notificationEventSeq, Integer registerUserSeq);
    
    NotificationReadStateFindQuery toNotificationReadStateFindQuery(Integer userSeq, boolean isRead);
}
