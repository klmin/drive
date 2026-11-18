package com.thinkfree.drive.postprocess.share.service.impl;

import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.service.NotificationService;
import com.thinkfree.drive.postprocess.share.converter.SharePostProcessingConverter;
import com.thinkfree.drive.postprocess.share.service.SharePostProcessingService;
import com.thinkfree.drive.postprocess.share.service.dto.*;
import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.share.target.data.ShareTargetResourceUsersData;
import com.thinkfree.drive.share.target.data.ShareTargetUserData;
import com.thinkfree.drive.share.target.service.ShareTargetFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultSharePostProcessingService implements SharePostProcessingService {
    
    private final SharePostProcessingConverter sharePostProcessingConverter;
    private final NotificationService notificationService;
    private final ShareTargetFindService shareTargetFindService;
    
    @Override
    public void processSharedAndNotify(ShareNotifyDto dto) {
        if(dto.getTargetUserSeqList() == null || dto.getTargetUserSeqList().isEmpty()){
            return;
        }
        var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto);
        notificationService.saveNotificationWithEvent(notificationEventSaveDto);
    }
    
    @Override
    public void processUnsharedAndNotify(UnshareNotifyDto dto) {
        if(dto.getTargetUserSeqList() == null || dto.getTargetUserSeqList().isEmpty()){
            return;
        }
        var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto);
        notificationService.saveNotificationWithEvent(notificationEventSaveDto);
    }
    
    @Override
    public void processUnsharedByMoveAndNotify(UnshareLookupNotifyDto dto) {
        
        var userSeq = dto.getUserSeq();
        var targetUserSeq = dto.getTargetUserSeq();
        var isDelete = false;
        
        if(dto.getResourceType().isFile()) {
            
            var resourceSeq = dto.getResourceSeqList().get(0);
            var shareTargetUsersDataList = shareTargetFindService.findShareTargetUsersByResource(resourceSeq,userSeq, targetUserSeq, isDelete);
            
            if (shareTargetUsersDataList.isEmpty()) {
                return;
            }
            
            var shareTargetUsers = shareTargetUsersDataList.stream()
                                                           .map(ShareTargetUserData::getUserSeq)
                                                           .toList();
            var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto, shareTargetUsers);
            notificationService.saveNotificationWithEvent(notificationEventSaveDto);
            return;
        }
        
        var shareTargetUserResourceDataList = shareTargetFindService.findShareTargetResourceUsersByResources(dto.getResourceSeqList(), userSeq, targetUserSeq, isDelete);
        
        if (shareTargetUserResourceDataList.isEmpty()) {
            return;
        }
        
        List<NotificationWithEventSaveDto> list = new ArrayList<>();
        
        for (var entry : shareTargetUserResourceDataList){
            var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto, entry.getTargetUsers(), entry.getResourceSeq(), entry.getResourceName());
            list.add(notificationEventSaveDto);
        }
        
        notificationService.saveNotificationWithEventBatch(list);
        
    }
    
    @Override
    public void processDeletedUnsharedAndNotify(DeletedUnshareNotifyDto dto){
        
        if(dto.getResourceBriefs() == null || dto.getResourceBriefs().isEmpty()){
            return;
        }
        
        boolean isFile = dto.getResourceType().isFile();
        var userSeq = dto.getUserSeq();
        
        if(isFile){
            var resourceBrief = dto.getResourceBriefs().get(0);
            var resourceSeq = resourceBrief.resourceSeq();
            var shareTargetResourceUsersDataList = shareTargetFindService.findShareTargetUsersByDeletedResource(resourceSeq, userSeq);
            
            if (shareTargetResourceUsersDataList.isEmpty()) {
                return;
            }
            
            var shareTargetUsers = shareTargetResourceUsersDataList.stream()
                                                                   .map(ShareTargetUserData::getUserSeq)
                                                                   .toList();
            
            var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto, resourceSeq, resourceBrief.resourceName(), shareTargetUsers);
            notificationService.saveNotificationWithEvent(notificationEventSaveDto);
            return;
        }
        
        var resourceBriefs = dto.getResourceBriefs();
        var resourceSeqList = resourceBriefs.stream().map(ResourceBrief::resourceSeq).toList();
        var shareTargetResourceUsersDataList = shareTargetFindService.findShareTargetResourceUsersByDeletedResources(resourceSeqList, userSeq);
        
        if(shareTargetResourceUsersDataList.isEmpty()){
            return;
        }
        
        var shareTargetResourceUsersDataMap = shareTargetResourceUsersDataList.stream().collect(Collectors.toMap(ShareTargetResourceUsersData::getResourceSeq, ShareTargetResourceUsersData::getTargetUsers));
        
        List<NotificationWithEventSaveDto> list = new ArrayList<>();
        
        for(var resourceBrief : resourceBriefs){
            
            var resourceSeq = resourceBrief.resourceSeq();
            var resourceName = resourceBrief.resourceName();
            var shareTargetUsers = shareTargetResourceUsersDataMap.get(resourceSeq);
            
            if(shareTargetUsers == null){
                continue;
            }
            
            var notificationEventSaveDto = sharePostProcessingConverter.toNotificationWithEventSaveDto(dto, resourceSeq, resourceName, shareTargetUsers);
            list.add(notificationEventSaveDto);
            
        }
        
        notificationService.saveNotificationWithEventBatch(list);
        
    }
    
    @Override
    public void processShareTargetChangedAndNotify(ShareTargetChangeNotifyDto dto) {
        var shareNotifyDto = sharePostProcessingConverter.toShareNotifyDto(dto);
        var unshareNotifyDto = sharePostProcessingConverter.toUnshareNotifyDto(dto);
        processSharedAndNotify(shareNotifyDto);
        processUnsharedAndNotify(unshareNotifyDto);
    }
    
}
