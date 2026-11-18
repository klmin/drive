package com.thinkfree.drive.notification.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.notification.command.EventWithNotifications;
import com.thinkfree.drive.notification.command.NotificationWithEventBatchCommand;
import com.thinkfree.drive.notification.data.NotificationData;
import com.thinkfree.drive.notification.entity.Notification;
import com.thinkfree.drive.notification.event.entity.NotificationEvent;
import com.thinkfree.drive.notification.event.enums.ActionType;
import com.thinkfree.drive.notification.event.repository.NotificationEventRepository;
import com.thinkfree.drive.notification.query.NotificationListQuery;
import com.thinkfree.drive.notification.query.NotificationReadStateFindQuery;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
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
 * 2단계 Notification + NotificationEvent 이관 회귀(§1 v2 "JPA 우선") — 쓰기({@link DefaultNotificationRepository}: JPA saveAll·단건 읽음 findByUserSeqAndNotificationSeq 관리 엔티티 변경 감지·전체 읽음 JPQL·IN 삭제·2-phase 배치, {@code DefaultNotificationEventRepository}: saveAndFlush·JPQL 삭제)·
 * 읽기({@link DefaultNotificationQueryRepository}: JdbcClient 3테이블 조인 커서 목록 + JPA count).
 * 시드 = user 1 superadmin·2 admin·3 anonymous(전부 ACTIVE). 공유자 가시성 EXISTS 는 user 3 을 DELETED 로 바꾸거나 없는 user_seq 로 검증. 리소스는 {@link ResourceRepository#save} 로 1건. 트랜잭션 롤백.
 * 검증 포인트 = identity 키·리스너 감사(엔티티 값 우선), jsonb description 왕복, Y/N 컨버터(저장·count·JPQL update 파라미터), 커서·isRead 필터·가시성, 읽음 처리 write-back·파티션 조건(userSeq), 2-phase 키 연결, INSERT_FAIL 변환(유니크 위반 맨 끝).
 */
@DisplayName("DefaultNotificationRepository / DefaultNotificationQueryRepository / DefaultNotificationEventRepository — JPA + JdbcClient 목록(PostgreSQL Testcontainers)")
class DefaultNotificationRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;
    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private NotificationQueryRepository queryRepository;
    @Autowired
    private NotificationEventRepository eventRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private Resource file(int userSeq, String name) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE).parentFolderSeq(null)
                .resourceName(name).originalFileName(name).fullPathSeq("/").fullPath("/" + name).copyNumber(0)
                .ext("txt").contentType("text/plain").sizeByte(5L).versionRegisterDate(Instant.now()).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        return r;
    }

    private static NotificationEvent event(Integer sharedBy, Long resourceSeq, ActionType type, String description) {
        return NotificationEvent.builder().resourceSeq(resourceSeq).sharedByUserSeq(sharedBy).actionType(type).description(description)
                .actorUserSeq(ADMIN).occurredDatetime(Instant.now()).registerUserSeq(ADMIN).build();
    }

    private static Notification notification(int userSeq, Long eventSeq) {
        return Notification.builder().userSeq(userSeq).notificationEventSeq(eventSeq).isRead(false).registerUserSeq(ADMIN).build();
    }

    private List<NotificationData> findAll(int userSeq, Boolean isRead, Long cursorId, int pageSize) {
        return queryRepository.findAll(NotificationListQuery.builder().userSeq(userSeq).isRead(isRead).page(CursorPaginationQuery.of(null, cursorId, null, pageSize)).build());
    }

    private int count(int userSeq, boolean isRead) {
        return queryRepository.findReadStateData(NotificationReadStateFindQuery.builder().userSeq(userSeq).isRead(isRead).build()).getReadStateCount();
    }

    private String column(String col, long notificationSeq) {
        return jdbc.queryForObject("SELECT CAST(" + col + " AS VARCHAR) FROM tb_notification WHERE notification_seq = ?", String.class, notificationSeq);
    }

    @Test
    @DisplayName("이벤트 save(identity·jsonb·리스너 register_date) → 알림 saveAll(Y/N 저장) → findAll 매핑(리소스 LEFT JOIN·공유자 가시성 EXISTS·isRead 필터·커서 DESC·LIMIT) · count · findByUserSeqAndNotificationSeq → changeReadState(변경 감지·Auditing, 타 사용자 null)/updateAllReadState(JPQL) · 유니크(user_seq, event_seq) 위반 → INSERT_FAIL 맨 끝")
    void saveListReadState() {
        Instant before = Instant.now().minusSeconds(1);
        Resource file = file(ADMIN, "notice.txt");

        NotificationEvent shared = eventRepository.save(event(ADMIN, file.getResourceSeq(), ActionType.SHARED, "{\"k\":\"v\"}"));
        assertThat(shared.getNotificationEventSeq()).as("identity 키").isNotNull().isPositive();
        assertThat(shared.getRegisterUserSeq()).as("컨버터가 넣은 actorUserSeq 우선").isEqualTo(ADMIN);
        assertThat(shared.getRegisterDate()).as("리스너").isNotNull().isAfter(before);
        assertThat(jdbc.queryForObject("SELECT description::text FROM tb_notification_event WHERE notification_event_seq = ?", String.class, shared.getNotificationEventSeq())).contains("\"k\"").contains("\"v\"");

        Notification n1 = notification(USER_A, shared.getNotificationEventSeq());
        Notification n1b = notification(USER_B, shared.getNotificationEventSeq());
        assertThat(repository.saveAll(List.of(n1, n1b))).isEqualTo(2);
        assertThat(n1.getNotificationSeq()).as("JDBC 배치 INSERT — 생성 키 미회수(XML 동일)").isNull();
        long n1Seq = jdbc.queryForObject("SELECT notification_seq FROM tb_notification WHERE user_seq = ? AND notification_event_seq = ?", Long.class, USER_A, shared.getNotificationEventSeq());
        long n1bSeq = jdbc.queryForObject("SELECT notification_seq FROM tb_notification WHERE user_seq = ? AND notification_event_seq = ?", Long.class, USER_B, shared.getNotificationEventSeq());
        assertThat(column("is_read", n1Seq)).as("false → N").isEqualTo("N");
        assertThat(column("register_user_seq", n1Seq)).as("등록 감사 — 엔티티 값 우선(배치 바인딩)").isEqualTo(String.valueOf(ADMIN));
        assertThat(column("register_date", n1Seq)).isNotNull();
        assertThat(repository.saveAll(List.of())).isZero();

        // 리소스 없는 이벤트(저장 용량) + 공유자가 없는 사용자(가시성 제외) + anonymous 공유자(ACTIVE → 포함, DELETED 로 바꾸면 제외)
        NotificationEvent quota = eventRepository.save(event(null, null, ActionType.STORAGE_WARNING, "{\"pct\":90}"));
        NotificationEvent ghost = eventRepository.save(event(999999, null, ActionType.UNSHARED, "{}"));
        NotificationEvent anon = eventRepository.save(event(ANONYMOUS, null, ActionType.FILE_UPLOAD, "{}"));
        Notification n2 = notification(USER_A, quota.getNotificationEventSeq());
        Notification n3 = notification(USER_A, ghost.getNotificationEventSeq());
        Notification n4 = notification(USER_A, anon.getNotificationEventSeq());
        assertThat(repository.saveAll(List.of(n2, n3, n4))).isEqualTo(3);
        long n2Seq = jdbc.queryForObject("SELECT notification_seq FROM tb_notification WHERE user_seq = ? AND notification_event_seq = ?", Long.class, USER_A, quota.getNotificationEventSeq());
        long n4Seq = jdbc.queryForObject("SELECT notification_seq FROM tb_notification WHERE user_seq = ? AND notification_event_seq = ?", Long.class, USER_A, anon.getNotificationEventSeq());

        // findAll — 가시성: 공유자 없음(n2: shared_by NULL → EXISTS false, XML 동일)·없는 사용자(n3) 제외, anonymous(ACTIVE) 포함 → n4, n1 (seq DESC)
        List<NotificationData> all = findAll(USER_A, null, null, 10);
        assertThat(all).extracting(NotificationData::getNotificationSeq).containsExactly(n4Seq, n1Seq);
        NotificationData d1 = all.get(1);
        assertThat(d1.getSharedByUserSeq()).isEqualTo(ADMIN);
        assertThat(d1.getResourceType()).isEqualTo(ResourceType.FILE);
        assertThat(d1.getResourceSeq()).isEqualTo(file.getResourceSeq());
        assertThat(d1.getResourceName()).isEqualTo("notice.txt");
        assertThat(d1.getActionType()).isEqualTo(ActionType.SHARED);
        assertThat(d1.getDescription()).contains("\"k\"");
        assertThat(d1.getIsRead()).isFalse();
        assertThat(d1.getRegisterUserSeq()).isEqualTo(ADMIN);
        assertThat(d1.getRegisterDate()).isNotNull().isAfter(before);
        assertThat(all.get(0).getResourceType()).as("리소스 없는 이벤트 LEFT JOIN").isNull();
        assertThat(all.get(0).getResourceName()).isNull();
        jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ANONYMOUS);
        assertThat(findAll(USER_A, null, null, 10)).extracting(NotificationData::getNotificationSeq).as("공유자 DELETED → 제외").containsExactly(n1Seq);
        jdbc.update("UPDATE tb_user SET status = 'ACTIVE' WHERE user_seq = ?", ANONYMOUS);
        assertThat(findAll(USER_B, null, null, 10)).extracting(NotificationData::getNotificationSeq).containsExactly(n1bSeq);
        assertThat(findAll(999, null, null, 10)).isEmpty();

        // 커서·LIMIT
        assertThat(findAll(USER_A, null, null, 1)).extracting(NotificationData::getNotificationSeq).containsExactly(n4Seq);
        assertThat(findAll(USER_A, null, n4Seq, 10)).extracting(NotificationData::getNotificationSeq).containsExactly(n1Seq);
        assertThat(findAll(USER_A, null, n1Seq, 10)).isEmpty();

        // count(JPA, Y/N 컨버터 파라미터) — 가시성과 무관하게 전부(XML 동일)
        assertThat(count(USER_A, false)).isEqualTo(4);
        assertThat(count(USER_A, true)).isZero();

        // 단건 읽음 — findByUserSeqAndNotificationSeq(파티션 JPQL) 관리 엔티티 → changeReadState → flush(커밋) 때 변경 감지, update_* 는 Auditing(시큐리티 사용자), 타 사용자는 null
        Instant readAt = Instant.parse("2026-04-04T04:04:04Z");
        Notification managed = repository.findByUserSeqAndNotificationSeq(USER_A, n1Seq);
        assertThat(entityManager.contains(managed)).as("관리 엔티티").isTrue();
        managed.changeReadState(true, readAt);
        assertThat(column("is_read", n1Seq)).as("flush 전 — 메모리만").isEqualTo("N");
        entityManager.flush();
        assertThat(column("is_read", n1Seq)).isEqualTo("Y");
        assertThat(column("update_user_seq", n1Seq)).isEqualTo(String.valueOf(ADMIN));
        assertThat(column("update_date", n1Seq)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT read_datetime FROM tb_notification WHERE notification_seq = ?", java.time.OffsetDateTime.class, n1Seq).toInstant()).isEqualTo(readAt);
        assertThat(repository.findByUserSeqAndNotificationSeq(USER_B, n1Seq)).as("타 사용자(파티션 조건)").isNull();
        assertThat(repository.findByUserSeqAndNotificationSeq(USER_A, 999999L)).isNull();
        assertThat(count(USER_A, true)).isEqualTo(1);
        assertThat(findAll(USER_A, true, null, 10)).extracting(NotificationData::getNotificationSeq).containsExactly(n1Seq);
        assertThat(findAll(USER_A, false, null, 10)).extracting(NotificationData::getNotificationSeq).containsExactly(n4Seq);
        assertThat(findAll(USER_A, null, null, 10).get(1).getIsRead()).isTrue();

        // updateAllReadState — 사용자 전체(가시성 무관 4건, 벌크 JPQL), update_* 는 시큐리티 사용자
        assertThat(repository.updateAllReadState(USER_A, true, readAt)).isEqualTo(4);
        assertThat(count(USER_A, false)).isZero();
        assertThat(count(USER_A, true)).isEqualTo(4);
        assertThat(column("update_user_seq", n2Seq)).isEqualTo(String.valueOf(ADMIN));
        assertThat(count(USER_B, false)).as("타 사용자 무영향").isEqualTo(1);

        // (user_seq, notification_event_seq) 유니크 위반 → INSERT_FAIL (PG tx abort → 맨 끝)
        assertThatThrownBy(() -> repository.saveAll(List.of(notification(USER_A, shared.getNotificationEventSeq())))).isInstanceOf(ApiRuntimeException.class);
    }

    @Test
    @DisplayName("2-phase 배치 — 이벤트 저장 → 생성 키를 알림에 연결 → 알림 저장(이벤트별) / deleteByUserSeqList·deleteBySharedByUserSeqList(IN·빈/null 목록 0)")
    void twoPhaseAndDeletes() {
        NotificationEvent e1 = event(ADMIN, null, ActionType.SHARED, "{\"n\":1}");
        NotificationEvent e2 = event(ANONYMOUS, null, ActionType.UNSHARED, "{\"n\":2}");
        List<Notification> ns1 = List.of(notification(USER_A, null), notification(USER_B, null));
        List<Notification> ns2 = List.of(notification(USER_A, null));
        repository.saveNotificationWithEventBatch2Phase(new NotificationWithEventBatchCommand(List.of(new EventWithNotifications(e1, ns1), new EventWithNotifications(e2, ns2))));
        assertThat(e1.getNotificationEventSeq()).isNotNull();
        assertThat(e2.getNotificationEventSeq()).isGreaterThan(e1.getNotificationEventSeq());
        assertThat(ns1).allSatisfy(n -> assertThat(n.getNotificationEventSeq()).as("생성 키 연결").isEqualTo(e1.getNotificationEventSeq()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_notification WHERE notification_event_seq = ? AND register_date IS NOT NULL AND register_user_seq = ?",
                Integer.class, e1.getNotificationEventSeq(), ADMIN)).as("배치 INSERT 등록 감사 바인딩(엔티티 write-back 은 W2 에서 제거)").isEqualTo(2);
        assertThat(ns2.get(0).getNotificationEventSeq()).isEqualTo(e2.getNotificationEventSeq());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_notification WHERE user_seq = ?", Integer.class, USER_A)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_notification WHERE user_seq = ?", Integer.class, USER_B)).isEqualTo(1);
        assertThat(findAll(USER_A, null, null, 10)).extracting(NotificationData::getActionType).containsExactly(ActionType.UNSHARED, ActionType.SHARED);
        repository.saveNotificationWithEventBatch2Phase(new NotificationWithEventBatchCommand(List.of()));
        repository.saveNotificationWithEventBatch2Phase(new NotificationWithEventBatchCommand(null));

        // deleteByUserSeqList
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(USER_A, 999999))).isEqualTo(2);
        assertThat(findAll(USER_A, null, null, 10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_notification WHERE user_seq = ?", Integer.class, USER_B)).as("타 사용자 유지").isEqualTo(1);

        // deleteBySharedByUserSeqList(이벤트)
        assertThat(eventRepository.deleteBySharedByUserSeqList(List.of())).isZero();
        assertThat(eventRepository.deleteBySharedByUserSeqList(null)).isZero();
        assertThat(eventRepository.deleteBySharedByUserSeqList(List.of(ANONYMOUS, 999999))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_notification_event WHERE notification_event_seq IN (?, ?)", Integer.class, e1.getNotificationEventSeq(), e2.getNotificationEventSeq())).isEqualTo(1);
    }
}
