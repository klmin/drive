package com.thinkfree.drive.notification.converter;

import com.thinkfree.drive.notification.dto.NotificationQuotaExceededDto;
import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.event.enums.ActionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {ActionType.class})
public interface NotificationRetryConverter {
    
    @Mapping(target = "actionType", expression = "java(ActionType.STORAGE_EXCEEDED)")
    @Mapping(target = "sharedByUserSeq", source = "userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(NotificationQuotaExceededDto dto);
}
