package com.thinkfree.drive.postprocess.quota.converter;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateChangeNotifyDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = {ActionType.class})
public interface QuotaPostProcessingConverter {
    QuotaStateChangeNotifyDto toQuotaStateChangeNotifyDto(Integer userSeq,  int actorUserSeq, Instant occurredDatetime);
    
    @Mapping(target = "actionType", expression = "java(ActionType.STORAGE_WARNING)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(QuotaStateChangeNotifyDto dto, int usagePercentage);
}
