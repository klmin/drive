package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.user.enums.UserStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_notification Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultNotificationRepository}(쓰기)·{@link DefaultNotificationQueryRepository}(count) 만 주입받아 위임한다.
 * UPDATE/DELETE JPQL·단건 로드는 파티션(HASH user_seq) 프루닝을 위해 항상 {@code userSeq} 조건을 건다(구 XML 동일). 단건 읽음 처리는 변경 감지(엔티티 {@code @PartitionKey} 로 UPDATE 에도 user_seq 조건). {@code isRead} 는 엔티티 컨버터(Y/N)가 파라미터에도 적용된다.
 * {@code @Modifying} 메서드는 {@code @Transactional} 필수(리포 기본 readOnly 트랜잭션의 UPDATE 를 PG 가 거부).
 */
interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    /**
     * 알림 목록(구 JdbcClient, 5차 패스 C) — 이벤트 INNER JOIN, 리소스 LEFT JOIN(공유자 파티션), 공유자 계정이 노출 가능 상태인 행만.
     * isRead·cursorId 는 선택(null 이면 조건 없음), notification_seq DESC.
     */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.notification.data.NotificationData(n.notificationSeq, e.sharedByUserSeq, r.resourceType, e.resourceSeq, r.resourceName, r.ext, "
            + "e.actionType, e.description, n.isRead, n.registerUserSeq, n.registerDate) "
            + "from Notification n join NotificationEvent e on e.notificationEventSeq = n.notificationEventSeq "
            + "left join Resource r on r.resourceSeq = e.resourceSeq and r.userSeq = e.sharedByUserSeq "
            + "where n.userSeq = :userSeq and (:isRead is null or n.isRead = :isRead) and (:cursorId is null or n.notificationSeq < :cursorId) "
            + "and exists (select 1 from User u where u.userSeq = e.sharedByUserSeq and u.status in :visibleStatuses) "
            + "order by n.notificationSeq desc")
    List<NotificationData> findList(@Param("userSeq") Integer userSeq, @Param("isRead") Boolean isRead, @Param("cursorId") Long cursorId,
                                    @Param("visibleStatuses") Collection<UserStatus> visibleStatuses, Limit limit);

    long countByUserSeqAndIsRead(Integer userSeq, Boolean isRead);

    @Transactional
    @Modifying
    @Query("update Notification n set n.isRead = :isRead, n.readDatetime = :readDatetime, n.updateUserSeq = :#{#stamp.userSeq}, n.updateDate = :#{#stamp.date} where n.userSeq = :userSeq")
    int updateAllReadState(@Param("userSeq") Integer userSeq, @Param("isRead") Boolean isRead, @Param("readDatetime") Instant readDatetime,
                           @Param("stamp") UpdateStamp stamp);

    /** 단건 로드(변경 감지용, 잠금 없음) — 파생 쿼리 — 파티션 키 user_seq 를 조건에 넣어 한 파티션만 */
    Optional<Notification> findByUserSeqAndNotificationSeq(Integer userSeq, Long notificationSeq);

    @Transactional
    @Modifying
    @Query("delete from Notification n where n.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") List<Integer> userSeqList);
}
