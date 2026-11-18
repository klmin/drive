package com.thinkfree.drive.notification.event.repository;

import com.thinkfree.drive.notification.event.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * tb_notification_event Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultNotificationEventRepository} 만 주입받아 위임한다.
 * IN 삭제는 JPQL {@code @Modifying}(메서드 {@code @Transactional} 필수 — 리포 기본 readOnly 트랜잭션의 DELETE 를 PG 가 거부).
 */
interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {

    @Transactional
    @Modifying
    @Query("delete from NotificationEvent e where e.sharedByUserSeq in :sharedByUserSeqList")
    int deleteBySharedByUserSeqIn(@Param("sharedByUserSeqList") List<Integer> sharedByUserSeqList);
}
