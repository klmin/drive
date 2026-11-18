package com.thinkfree.drive.notification.event.repository;

import com.thinkfree.drive.notification.event.entity.NotificationEvent;

import java.util.List;

public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent entity);
    int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList);
}
