package com.thinkfree.drive.notification.service;

import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.data.NotificationReadStateData;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;

import java.util.List;

public interface NotificationFindService {
    
    List<NotificationData> findAll(NotificationListQuery query);
    NotificationReadStateData findReadStateData(NotificationReadStateFindQuery query);
}
