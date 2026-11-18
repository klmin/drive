package com.thinkfree.drive.postprocess.upload.service.impl;

import com.thinkfree.drive.notification.service.NotificationService;
import com.thinkfree.drive.postprocess.upload.converter.UploadPostProcessingConverter;
import com.thinkfree.drive.postprocess.upload.dto.SharingUploadNotifyDto;
import com.thinkfree.drive.postprocess.upload.service.UploadPostProcessingService;
import com.thinkfree.drive.share.data.ShareUserData;
import com.thinkfree.drive.share.service.ShareFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DefaultUploadPostProcessingService implements UploadPostProcessingService {
    
    private final UploadPostProcessingConverter uploadPostProcessingConverter;
    private final ShareFindService shareFindService;
    private final NotificationService notificationService;
    
    /**
     * 공유 폴더에 파일이 업로드 되면 상향 탐색 후 유저들에게 알림.
     * 추후에 사용하기로 함.
     * @param dto 공유 폴더 파일 업로드 알림
     */
    @Override
    public void processSharedUploadAndNotify(SharingUploadNotifyDto dto) {
        
        if(dto.getParentFolderSeq() == null){
            return;
        }
        
        var sharedByUserSeq = dto.getUserSeq();
        var shareTargetUsersData = shareFindService.findSharedUsersByResource(dto.getParentFolderSeq(), sharedByUserSeq, dto.getIsDelete());
        
        if(!shareTargetUsersData.isEmpty()) {
            var shareTargetUsers = shareTargetUsersData.stream().map(ShareUserData::getUserSeq).collect(Collectors.toSet());
            var notificationEventSaveDto = uploadPostProcessingConverter.toNotificationWithEventSaveDto(dto, shareTargetUsers);
            notificationService.saveNotificationWithEvent(notificationEventSaveDto);
        }
    }
}
