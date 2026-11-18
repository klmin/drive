package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.data.NotificationReadStateData;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_notification 읽기 — 전부 JPA(findAll = 이벤트·리소스·사용자 엔티티 조인 JPQL 생성자 식(5차 패스 C), findReadStateData = count). 쓰기는 {@link DefaultNotificationRepository}.
 * <p>
 * findAll(구 NotificationMapper.xml): 이벤트 INNER JOIN, 리소스 LEFT JOIN(resource_seq + 공유자 = 소유자), 공유자 계정이 노출 가능 상태(ACTIVE/INACTIVE)인 행만(구 {@code CommonMapper.resource-visible-by-active-user}),
 * {@code isRead} 가 있을 때만 필터, {@code page.cursorId} 가 있을 때만 {@code notification_seq < cursorId}, notification_seq DESC, page.pageSize 건(null 이면 제한 없음 — 구 FETCH FIRST NULL 과 같음).
 */
@Repository
@RequiredArgsConstructor
public class DefaultNotificationQueryRepository implements NotificationQueryRepository {

    private static final List<UserStatus> VISIBLE_SHARER_STATUSES = List.of(UserStatus.ACTIVE, UserStatus.INACTIVE);

    private final NotificationJpaRepository jpaRepository;

    @Override
    public List<NotificationData> findAll(NotificationListQuery q) {
        CursorPaginationQuery page = q.getPage();
        Limit limit = page.getPageSize() == null ? Limit.unlimited() : Limit.of(page.getPageSize());
        return jpaRepository.findList(q.getUserSeq(), q.getIsRead(), page.getCursorId(), VISIBLE_SHARER_STATUSES, limit);
    }

    @Override
    public NotificationReadStateData findReadStateData(NotificationReadStateFindQuery q) {
        long count = jpaRepository.countByUserSeqAndIsRead(q.getUserSeq(), q.getIsRead());
        return NotificationReadStateData.builder().readStateCount((int) count).build();
    }
}
