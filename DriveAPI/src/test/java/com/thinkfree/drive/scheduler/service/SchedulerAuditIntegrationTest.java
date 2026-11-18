package com.thinkfree.drive.scheduler.service;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 스케줄러 3종(잠금 만료·삭제 대기 사용자 처리·휴지통 자동 비우기)을 서비스 메서드 직접 호출로 실행해 감사 값을 확인한다(10-07 작업 C-4).
 * 시큐리티 컨텍스트 없이 실행 → 감사 값은 코드가 선언한 SUPER_ADMIN 이 그대로 들어가야 한다(시큐리티 사용자 우선 규칙의 "없으면 선언 값" 쪽).
 * 데이터 준비만 시큐리티 사용자(ADMIN)로 하고, 실행 직전에 컨텍스트를 비운다. 트랜잭션 롤백(Testcontainers).
 * ShedLock {@code LockProvider} 는 목으로 바꾼다 — 실제 JdbcTemplateLockProvider 는 테스트 트랜잭션의 커넥션에서 락 SQL 을 커밋해 준비 데이터까지 커밋시킨다
 * (운영은 스케줄러 스레드라 바깥 트랜잭션이 없어 무관).
 */
@DisplayName("스케줄러 감사 — 시큐리티 없이 선언 값(SUPER_ADMIN)이 들어가는지")
class SchedulerAuditIntegrationTest extends ServiceTest {

    private static final int SUPER_ADMIN = DriveConstants.SUPER_ADMIN_USER_SEQ;
    private static final int ADMIN = 2;
    private static final int TENANT = 1;

    @Autowired
    private SchedulerService schedulerService;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private LockProvider lockProvider;
    @MockitoSpyBean
    private ResourceAuditService resourceAuditService;
    @MockitoSpyBean
    private UserAuditService userAuditService;

    @BeforeEach
    void noOpSchedulerLock() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {
        }));
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

    private Resource file(int userSeq, String name, long size) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .resourceName(name).originalFileName(name).copyNumber(0).ext("txt").contentType("text/plain").sizeByte(size)
                .versionRegisterDate(Instant.now()).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    private <T> T resourceCol(long resourceSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource WHERE resource_seq = ?", type, resourceSeq);
    }

    private <T> T userCol(int userSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_user WHERE user_seq = ?", type, userSeq);
    }

    @Test
    @DisplayName("잠금 만료(cleanupResourceLocks) — 만료된 잠금만 해제, update_user_seq = SUPER_ADMIN")
    void cleanupResourceLocks() {
        loginAs(ADMIN);
        Resource expired = file(ADMIN, "sched-lock-expired.txt", 10L);
        Resource fresh = file(ADMIN, "sched-lock-fresh.txt", 10L);
        Timestamp old = Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS));
        jdbc.update("UPDATE tb_resource SET is_locked = 'Y', locked_date = ?, update_user_seq = ? WHERE resource_seq = ?", old, ADMIN, expired.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET is_locked = 'Y', locked_date = ?, update_user_seq = ? WHERE resource_seq = ?", Timestamp.from(Instant.now()), ADMIN, fresh.getResourceSeq());
        SecurityContextHolder.clearContext();

        schedulerService.cleanupResourceLocks();

        assertThat(resourceCol(expired.getResourceSeq(), "is_locked", String.class)).isEqualTo("N");
        assertThat(resourceCol(expired.getResourceSeq(), "locked_date", Timestamp.class)).isNull();
        assertThat(resourceCol(expired.getResourceSeq(), "update_user_seq", Integer.class)).as("선언 값 SUPER_ADMIN").isEqualTo(SUPER_ADMIN);
        assertThat(resourceCol(expired.getResourceSeq(), "update_date", Timestamp.class)).isNotNull();
        assertThat(resourceCol(fresh.getResourceSeq(), "is_locked", String.class)).as("만료 전 잠금은 그대로").isEqualTo("Y");
        assertThat(resourceCol(fresh.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("삭제 대기 사용자 처리(cleanupPendingDeleteUsers) — 예정 시각이 지난 사용자만 DELETED, update_user_seq = SUPER_ADMIN, 감사 로그 actor = SUPER_ADMIN")
    void cleanupPendingDeleteUsers() {
        loginAs(ADMIN);
        User due = User.createUser("sched-pending-due@example.test", TENANT, Password.of("x"), SignupType.LOCAL, "due", UserStatus.ACTIVE, 1000L,
                ADMIN, null, null, null);
        User notYet = User.createUser("sched-pending-later@example.test", TENANT, Password.of("x"), SignupType.LOCAL, "later", UserStatus.ACTIVE, 1000L,
                ADMIN, null, null, null);
        assertThat(userRepository.insertUserWithSeatCheck(due, false, 0)).isTrue();
        assertThat(userRepository.insertUserWithSeatCheck(notYet, false, 0)).isTrue();
        jdbc.update("UPDATE tb_user SET status = 'PENDING_DELETE', delete_schedule_time = ?, update_user_seq = ? WHERE user_seq = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), ADMIN, due.getUserSeq());
        jdbc.update("UPDATE tb_user SET status = 'PENDING_DELETE', delete_schedule_time = ?, update_user_seq = ? WHERE user_seq = ?",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)), ADMIN, notYet.getUserSeq());
        SecurityContextHolder.clearContext();

        schedulerService.cleanupPendingDeleteUsers();

        assertThat(userCol(due.getUserSeq(), "status", String.class)).isEqualTo("DELETED");
        assertThat(userCol(due.getUserSeq(), "update_user_seq", Integer.class)).as("선언 값 SUPER_ADMIN").isEqualTo(SUPER_ADMIN);
        assertThat(userCol(due.getUserSeq(), "delete_date", Timestamp.class)).isNotNull();
        assertThat(userCol(notYet.getUserSeq(), "status", String.class)).as("예정 전은 그대로").isEqualTo("PENDING_DELETE");
        assertThat(userCol(notYet.getUserSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
        verify(userAuditService).record(eq(EventName.USER_PERMANENT_DELETE), argThat(t -> due.getUserSeq().equals(t.getUserSeq())),
                argThat(actor -> actor != null && actor.getUserSeq() == SUPER_ADMIN), eq(TENANT));
    }

    @Test
    @DisplayName("휴지통 자동 비우기(cleanUpTrash) — 보관 기간이 지난 휴지통 항목만 영구 삭제·용량 차감, 감사 컬럼 쓰기 없음, 감사 로그 actor = SUPER_ADMIN")
    void cleanUpTrash() {
        loginAs(ADMIN);
        Resource old = file(ADMIN, "sched-trash-old.txt", 7L);
        Resource recent = file(ADMIN, "sched-trash-recent.txt", 5L);
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq IN (?, ?)", old.getResourceSeq(), recent.getResourceSeq());
        Timestamp beyond = Timestamp.from(Instant.now().minus(DriveConstants.DEFAULT_TRASH_CLEAN_UP_INTERVAL_DAY + 1L, ChronoUnit.DAYS));
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq, register_date) VALUES (?, ?, ?, ?)", ADMIN, old.getResourceSeq(), ADMIN, beyond);
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq, register_date) VALUES (?, ?, ?, ?)", ADMIN, recent.getResourceSeq(), ADMIN, Timestamp.from(Instant.now()));
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000 WHERE user_seq = ?", ADMIN);
        SecurityContextHolder.clearContext();

        schedulerService.cleanUpTrash();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE resource_seq = ?", Integer.class, old.getResourceSeq())).as("영구 삭제").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_trash WHERE resource_seq = ?", Integer.class, old.getResourceSeq())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE resource_seq = ?", Integer.class, recent.getResourceSeq())).as("보관 기간 안은 그대로").isEqualTo(1);
        assertThat(userCol(ADMIN, "use_quota_byte", Long.class)).as("용량 차감").isEqualTo(1000L - 7L);
        verify(resourceAuditService).recordPermanentDelete(eq(EventName.FILE_PERMANENT_DELETE), eq(old.getResourceSeq()), any(), eq(TENANT),
                argThat(actor -> actor != null && actor.getUserSeq() == SUPER_ADMIN));
    }
}
