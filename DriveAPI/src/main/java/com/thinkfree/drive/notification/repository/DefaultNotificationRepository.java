package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.jooq.tables.records.TbNotificationRecord;
import com.thinkfree.drive.notification.command.NotificationWithEventBatchCommand;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.event.repository.NotificationEventRepository;
import com.thinkfree.drive.notification.exception.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_NOTIFICATION;

/**
 * tb_notification 쓰기 — JPA + JDBC 배치(§1 v2, 구 NotificationMapper.xml 의 save·updateAllReadState·updateReadState·deleteByUserSeqList + MyBatis 배치 2곳). 읽기는 {@link DefaultNotificationQueryRepository}.
 * <p>
 * <ul>
 *   <li>{@code saveAll}·{@code saveNotificationWithEventBatch2Phase} 의 알림 N건 INSERT 는 {@link JooqBatches#insert}(jOOQ 레코드 배치 — 진짜 JDBC 배치, 구 MyBatis 배치 executor 자리).
 *       JPA {@code saveAll} 을 쓰지 않는 이유 = 식별자가 IDENTITY 라 Hibernate 는 {@code hibernate.jdbc.batch_size} 가 있어도 INSERT 를 배치하지 못한다(행마다 생성 키 회수). 알림 행은 생성 키가 필요 없어 배치가 맞다.
 *       2-phase 는 이벤트를 {@link NotificationEventRepository#save}(JPA save, IDENTITY 라 저장 순간 키 회수) 한 뒤 알림에 연결해 배치 INSERT. 등록 감사는 {@link JdbcAudit}. DB 예외는 구 배치와 같이 {@code NotificationErrorCode.INSERT_FAIL}(구 saveAll 은 ShareTargetErrorCode.INSERT_FAIL 을 던졌음 — 복붙 오류라 통일)</li>
 *   <li>읽음 처리: 전체는 JPQL {@code @Modifying}(조건부 다건, update_* 는 {@link UpdateStamp}), 단건은 {@code findByUserSeqAndNotificationSeq} 관리 엔티티를 서비스가 고쳐 커밋 때 변경 감지(W2 10-12, {@code @PartitionKey} 로 UPDATE 에 user_seq 조건, 감사는 Auditing)</li>
 *   <li>IN 삭제: 빈 목록이면 SQL 없이 0</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final DSLContext dsl;

    @Override
    public int saveAll(List<Notification> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        try {
            batchInsert(entities);
        } catch (DataAccessException e) {
            log.error("[알림 대상 데이터 저장 실패] {}건", entities.size(), e);
            throw ApiRuntimeException.type(NotificationErrorCode.INSERT_FAIL);
        }
        log.info("[알림 대상 데이터 저장] : {}건", entities.size());
        return entities.size();
    }

    /** 사용자의 알림 전체(조건부 다건)라 벌크 JPQL — 변경 감지 대상이 아님. 수정 감사는 {@link UpdateStamp}(시큐리티 사용자·현재 시각) */
    @Override
    public int updateAllReadState(Integer userSeq, Boolean isRead, Instant readDatetime) {
        return jpaRepository.updateAllReadState(userSeq, isRead, readDatetime, UpdateStamp.of(null, null));
    }

    /** 파티션 테이블이라 (user_seq, notification_seq) JPQL 로드(em.find 는 user_seq 를 모름). 서비스가 {@link Notification#changeReadState} → 커밋 때 변경 감지 */
    @Override
    public Notification findByUserSeqAndNotificationSeq(Integer userSeq, Long notificationSeq) {
        return jpaRepository.findByUserSeqAndNotificationSeq(userSeq, notificationSeq).orElse(null);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public void saveNotificationWithEventBatch2Phase(NotificationWithEventBatchCommand command) {
        if (command.eventWithNotificationsList() == null || command.eventWithNotificationsList().isEmpty()) {
            return;
        }
        int notificationSaveCountTotal = 0;
        try {
            for (var eventWithNotifications : command.eventWithNotificationsList()) {
                var notificationEvent = notificationEventRepository.save(eventWithNotifications.notificationEvent());
                Long notificationEventSeq = notificationEvent.getNotificationEventSeq();
                var notifications = eventWithNotifications.notifications();
                notifications.forEach(n -> n.changeNotificationEventSeq(notificationEventSeq));
                batchInsert(notifications);
                notificationSaveCountTotal += notifications.size();
                log.info("알림 이벤트 등록 Seq : {}, 알림 대상 유저 : {} 명", notificationEventSeq, notifications.size());
            }
        } catch (DataAccessException e) {
            log.error("[알림 이벤트+알림 2-phase 저장 실패] 이벤트 {}건", command.eventWithNotificationsList().size(), e);
            throw ApiRuntimeException.type(NotificationErrorCode.INSERT_FAIL);
        }
        log.info("총 알림 대상 유저 : {} 명", notificationSaveCountTotal);
    }

    /** 알림 N건 JDBC 배치 INSERT(IDENTITY 라 JPA 는 배치 불가, {@link JooqBatches#insert} 레코드 배치) — 등록 감사(비면 컨텍스트 사용자·현재 시각)를 바인딩. 생성 키는 돌려받지 않는다(호출부가 안 읽음, 구 XML 동일). */
    private void batchInsert(List<Notification> entities) {
        if (entities.isEmpty()) {
            return;
        }
        JooqBatches.insert(dsl, entities, this::toRecord);
    }

    private TbNotificationRecord toRecord(Notification e) {
        TbNotificationRecord r = dsl.newRecord(TB_NOTIFICATION);
        r.setUserSeq(e.getUserSeq());
        r.setNotificationEventSeq(e.getNotificationEventSeq());
        r.setIsRead(e.getIsRead());
        r.setRegisterUserSeq(JdbcAudit.userSeqOr(e.getRegisterUserSeq()));
        r.setRegisterDate(JdbcAudit.nowOr(e.getRegisterDate()));
        return r;
    }
}
