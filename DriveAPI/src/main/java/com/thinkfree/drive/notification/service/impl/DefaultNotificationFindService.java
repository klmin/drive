package com.thinkfree.drive.notification.service.impl;

import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.data.NotificationReadStateData;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;
import com.thinkfree.drive.notification.repository.NotificationQueryRepository;
import com.thinkfree.drive.notification.service.NotificationFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultNotificationFindService implements NotificationFindService {
    
    private final NotificationQueryRepository notificationQueryRepository;
    
    @Override
    public List<NotificationData> findAll(NotificationListQuery query) {
        return notificationQueryRepository.findAll(query);
    }
    
    @Override
    public NotificationReadStateData findReadStateData(NotificationReadStateFindQuery query) {
        return notificationQueryRepository.findReadStateData(query);
    }
    
}
