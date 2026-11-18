package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.notification.command.NotificationWithEventBatchCommand;
import com.thinkfree.drive.notification.entity.Notification;

import java.time.Instant;
import java.util.List;

/**
 * tb_notification 쓰기 + 쓰기에 묶인 조회 — {@code DefaultNotificationService} 가 주입. 읽기는 {@link NotificationQueryRepository}.
 */
public interface NotificationRepository {

    /** 알림 N건 저장(이벤트 seq 는 이미 채워진 엔티티). 실패 시 NotificationErrorCode.INSERT_FAIL */
    int saveAll(List<Notification> entities);

    /** 사용자 알림 전체 읽음/안읽음 — 수정 감사는 시큐리티 사용자·현재 시각 */
    int updateAllReadState(Integer userSeq, Boolean isRead, Instant readDatetime);

    /** 단건 읽음 처리용 관리 엔티티 — (user_seq, notification_seq) 파티션 로드. 없으면 null */
    Notification findByUserSeqAndNotificationSeq(Integer userSeq, Long notificationSeq);

    int deleteByUserSeqList(List<Integer> userSeqList);

    /** 2-phase: 이벤트 저장 → 생성 키를 알림에 연결 → 알림 저장(이벤트별). 실패 시 NotificationErrorCode.INSERT_FAIL */
    void saveNotificationWithEventBatch2Phase(NotificationWithEventBatchCommand command);
}
