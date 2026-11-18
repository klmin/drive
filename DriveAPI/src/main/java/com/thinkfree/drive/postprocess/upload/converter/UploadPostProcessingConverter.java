package com.thinkfree.drive.postprocess.upload.converter;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.postprocess.upload.dto.SharingUploadNotifyDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;

@Mapper(componentModel = "spring", imports = {ActionType.class})
public interface UploadPostProcessingConverter {
    
    @Mapping(target = "actionType", expression = "java(ActionType.FILE_UPLOAD)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(SharingUploadNotifyDto dto, Collection<Integer> targetUserSeqList);
}
