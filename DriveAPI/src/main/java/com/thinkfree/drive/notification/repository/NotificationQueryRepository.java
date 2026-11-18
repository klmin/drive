package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.data.NotificationReadStateData;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;

import java.util.List;

/**
 * tb_notification 읽기 — {@code DefaultNotificationFindService} 가 주입. 쓰기는 {@link NotificationRepository}.
 */
public interface NotificationQueryRepository {

    /** 알림 목록(이벤트·리소스 조인), isRead 선택 필터, notification_seq DESC 커서 */
    List<NotificationData> findAll(NotificationListQuery query);

    /** 사용자의 읽음/안읽음 건수 */
    NotificationReadStateData findReadStateData(NotificationReadStateFindQuery query);
}
