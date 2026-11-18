package com.thinkfree.drive.notification.event.service;

import com.thinkfree.drive.notification.event.dto.NotificationEventSaveDto;

import java.util.List;

public interface NotificationEventService {
    Long save(NotificationEventSaveDto dto);
    void deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
}
