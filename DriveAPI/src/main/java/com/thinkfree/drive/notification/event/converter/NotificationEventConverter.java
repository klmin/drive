package com.thinkfree.drive.notification.event.converter;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.event.dto.NotificationEventSaveDto;
import com.thinkfree.drive.notification.event.entity.NotificationEvent;
import com.thinkfree.drive.notification.payload.NotificationPayload;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ObjectMapperUtil.class)
public interface NotificationEventConverter {
    NotificationEventSaveDto notificationEventToSaveDto(NotificationWithEventSaveDto dto, NotificationPayload payload);
    
    @Mapping(target = "description", source = "payload", qualifiedByName = "writeValueAsString")
    @Mapping(target = "registerUserSeq", source = "actorUserSeq")
    NotificationEvent toSaveNotification(NotificationEventSaveDto dto);
}
