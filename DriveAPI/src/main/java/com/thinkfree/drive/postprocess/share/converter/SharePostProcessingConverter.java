package com.thinkfree.drive.postprocess.share.converter;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.postprocess.share.service.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {ActionType.class})
public interface SharePostProcessingConverter {
    
    @Mapping(target = "actionType", expression = "java(ActionType.SHARED)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(ShareNotifyDto dto);
    
    @Mapping(target = "actionType", expression = "java(ActionType.UNSHARED)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(UnshareNotifyDto dto);
    
    @Mapping(target = "actionType", expression = "java(ActionType.UNSHARED)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(UnshareLookupNotifyDto dto, List<Integer> targetUserSeqList);
    
    @Mapping(target = "actionType", expression = "java(ActionType.UNSHARED)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    @Mapping(target = "resourceSeq", source = "resourceSeq")
    @Mapping(target = "fileName", source = "fileName")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(UnshareLookupNotifyDto dto, List<Integer> targetUserSeqList, Long resourceSeq, String fileName);
    
    @Mapping(target = "actionType", expression = "java(ActionType.UNSHARED)")
    @Mapping(target = "sharedByUserSeq", source = "dto.userSeq")
    NotificationWithEventSaveDto toNotificationWithEventSaveDto(DeletedUnshareNotifyDto dto, long resourceSeq, String fileName, List<Integer> targetUserSeqList);
    
    @Mapping(target="targetUserSeqList", source = "addedTargetUserSeqList")
    ShareNotifyDto toShareNotifyDto(ShareTargetChangeNotifyDto dto);
    @Mapping(target="targetUserSeqList", source = "deletedTargetUserSeqList")
    UnshareNotifyDto toUnshareNotifyDto(ShareTargetChangeNotifyDto dto);
}
