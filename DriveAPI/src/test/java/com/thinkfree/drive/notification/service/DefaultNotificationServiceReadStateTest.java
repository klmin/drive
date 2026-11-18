package com.thinkfree.drive.notification.service;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.notification.dto.NotificationAllReadDto;
import com.thinkfree.drive.notification.dto.NotificationReadDto;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.event.entity.NotificationEvent;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.event.repository.NotificationEventRepository;
import com.thinkfree.drive.notification.exception.NotificationErrorCode;
import com.thinkfree.drive.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * W2(10-12) — 단건 읽음/안 읽음이 쓰기 리포의 관리 엔티티를 고쳐 변경 감지로 반영되고, 같은 트랜잭션의 안 읽은 수(JPQL count)가 실행 직전 자동 flush 로 바뀐 값을 세는지 서비스 경유로 확인.
 * 트랜잭션 롤백 — DB 값은 flush 후 JdbcTemplate 으로 확인.
 */
@DisplayName("DefaultNotificationService — 단건 읽음 변경 감지 + 안 읽은 수(자동 flush), 없는 알림 오류, 전체 읽음")
class DefaultNotificationServiceReadStateTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int USER = 910001;

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationEventRepository eventRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private long n1;
    private long n2;

    @BeforeEach
    void setUp() {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));

        for (int i = 0; i < 2; i++) {
            NotificationEvent event = eventRepository.save(NotificationEvent.builder().actionType(ActionType.STORAGE_WARNING).description("{}")
                    .actorUserSeq(ADMIN).occurredDatetime(Instant.now()).registerUserSeq(ADMIN).build());
            notificationRepository.saveAll(List.of(Notification.builder().userSeq(USER).notificationEventSeq(event.getNotificationEventSeq())
                    .isRead(false).registerUserSeq(ADMIN).build()));
        }
        List<Long> seqs = jdbc.queryForList("SELECT notification_seq FROM tb_notification WHERE user_seq = ? ORDER BY notification_seq", Long.class, USER);
        n1 = seqs.get(0);
        n2 = seqs.get(1);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String column(String col, long seq) {
        return jdbc.queryForObject("SELECT CAST(" + col + " AS VARCHAR) FROM tb_notification WHERE notification_seq = ?", String.class, seq);
    }

    @Test
    @DisplayName("읽음 → 안 읽은 수 1(같은 트랜잭션 count 가 자동 flush 로 반영), DB is_read Y·update_* Auditing / 안 읽음 → 2 / 없는 알림·남의 알림 READ_FAIL·UNREAD_FAIL")
    void applyReadStateAndCountUnread() {
        Instant readAt = Instant.parse("2026-05-05T05:05:05Z");
        var unread = notificationService.applyReadStateAndCountUnread(NotificationReadDto.builder().notificationSeq(n1).userSeq(USER).isRead(true).readDatetime(readAt).build());
        assertThat(unread.unreadCount()).as("변경 감지 값이 count 직전 자동 flush 로 반영").isEqualTo(1);
        entityManager.flush();
        assertThat(column("is_read", n1)).isEqualTo("Y");
        assertThat(column("update_user_seq", n1)).as("Auditing — 시큐리티 사용자").isEqualTo(String.valueOf(ADMIN));
        assertThat(column("update_date", n1)).isNotNull();
        assertThat(column("is_read", n2)).isEqualTo("N");

        var back = notificationService.applyReadStateAndCountUnread(NotificationReadDto.builder().notificationSeq(n1).userSeq(USER).isRead(false).build());
        assertThat(back.unreadCount()).isEqualTo(2);

        assertThatThrownBy(() -> notificationService.applyReadStateAndCountUnread(NotificationReadDto.builder().notificationSeq(999_999_999L).userSeq(USER).isRead(true).readDatetime(readAt).build()))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(NotificationErrorCode.READ_FAIL));
        assertThatThrownBy(() -> notificationService.applyReadStateAndCountUnread(NotificationReadDto.builder().notificationSeq(n2).userSeq(USER + 1).isRead(false).build()))
                .as("남의 알림(파티션 조건)")
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(NotificationErrorCode.UNREAD_FAIL));
    }

    @Test
    @DisplayName("전체 읽음 — 벌크 JPQL 로 사용자 알림 전부 Y, 안 읽은 수 0")
    void markAllAsRead() {
        notificationService.markAllAsRead(NotificationAllReadDto.builder().userSeq(USER).isRead(true).readDatetime(Instant.now()).build());
        assertThat(notificationService.countUnread(USER).unreadCount()).isZero();
        assertThat(column("is_read", n1)).isEqualTo("Y");
        assertThat(column("update_user_seq", n2)).isEqualTo(String.valueOf(ADMIN));
    }
}
