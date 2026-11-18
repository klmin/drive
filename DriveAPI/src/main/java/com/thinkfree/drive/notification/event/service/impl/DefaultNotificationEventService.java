package com.thinkfree.drive.notification.event.service.impl;

import com.thinkfree.drive.notification.event.converter.NotificationEventConverter;
import com.thinkfree.drive.notification.event.dto.NotificationEventSaveDto;
import com.thinkfree.drive.notification.event.repository.NotificationEventRepository;
import com.thinkfree.drive.notification.event.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultNotificationEventService implements NotificationEventService {
    
    private final NotificationEventConverter notificationEventConverter;
    private final NotificationEventRepository notificationEventRepository;
    
    @Override
    public Long save(NotificationEventSaveDto dto) {
        var entity = notificationEventConverter.toSaveNotification(dto);
        return notificationEventRepository.save(entity).getNotificationEventSeq();
    }
    
    @Override
    public void deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        var result = notificationEventRepository.deleteBySharedByUserSeqList(sharedByUserSeqList);
        log.info("[알림 이벤트 삭제 : {}건]", result);
    }
}
