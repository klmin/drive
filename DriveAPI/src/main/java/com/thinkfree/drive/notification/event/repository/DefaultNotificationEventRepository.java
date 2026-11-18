package com.thinkfree.drive.notification.event.repository;

import com.thinkfree.drive.notification.event.entity.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_notification_event 쓰기 — JPA 위임(§1 v2, 구 NotificationEventMapper.xml 의 save·deleteBySharedByUserSeqList).
 * {@code save} 는 Spring Data {@code save}(IDENTITY 라 저장 순간 INSERT) — identity 키가 엔티티에 채워져 돌아오고(구 XML {@code useGeneratedKeys}), register_* 는 Spring Data Auditing(시큐리티 사용자 우선 — 컨버터가 넣은 actorUserSeq 는 시큐리티 사용자가 없을 때만 남음).
 * 알림 2-phase 저장({@code DefaultNotificationRepository})도 이 리포의 save 를 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class DefaultNotificationEventRepository implements NotificationEventRepository {

    private final NotificationEventJpaRepository jpaRepository;

    @Override
    public NotificationEvent save(NotificationEvent entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public int deleteBySharedByUserSeqList(List<Integer> sharedByUserSeqList) {
        if (sharedByUserSeqList == null || sharedByUserSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteBySharedByUserSeqIn(sharedByUserSeqList);
    }
}
