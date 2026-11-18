package com.thinkfree.drive.notification.service;

import com.thinkfree.drive.notification.dto.NotificationAllReadDto;
import com.thinkfree.drive.notification.dto.NotificationReadDto;
import com.thinkfree.drive.notification.dto.NotificationWithEventSaveDto;
import com.thinkfree.drive.notification.result.NotificationUnreadResult;

import java.util.List;

public interface NotificationService {
    
    void saveNotificationWithEvent(NotificationWithEventSaveDto dto);
    void saveNotificationWithEventBatch(List<NotificationWithEventSaveDto> list);
    void markAllAsRead(NotificationAllReadDto dto);
    NotificationUnreadResult applyReadStateAndCountUnread(NotificationReadDto dto);
    NotificationUnreadResult countUnread(int userSeq);
    void deleteByUsers(List<Integer> userSeqList);
}
