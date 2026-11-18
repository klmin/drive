package com.thinkfree.drive.api.v1.notification.converter;

import com.thinkfree.drive.api.v1.notification.response.NotificationListResponse;
import com.thinkfree.drive.notification.data.NotificationData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationResponseConverter {
    
    List<NotificationListResponse> toNotificationListResponse(List<NotificationData> list);
}
