package com.thinkfree.drive.userlog.repository;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.userlog.data.UserLogData;
import com.thinkfree.drive.userlog.entity.UserLog;
import com.thinkfree.drive.userlog.enums.ActivityType;
import com.thinkfree.drive.userlog.enums.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 2단계 UserLog 이관 회귀 — §1 v2 "JPA 우선", 구 XML 3 statement 전부 JPA(JdbcClient 0). 읽기({@link DefaultUserLogQueryRepository}: JPQL 생성자 식 커서 목록 + {@code Limit}, 파생 exists)·쓰기({@link DefaultUserLogRepository}: saveAndFlush).
 * tb_user_log.user_seq 는 tb_user FK → 시드 user 2 admin·3 anonymous 사용. 트랜잭션 롤백.
 * 검증 포인트 = identity Long·register_date DB DEFAULT(Instant 왕복)·enum 이름 저장, 목록 register_date DESC·ps 제한·ci 커서(user_log_seq 미만)·사용자 격리·DTO 문자열 enum, existsChangePasswordLog/existsLoginLog(반대 의미) 계약, FK 위반은 맨 끝.
 */
@DisplayName("DefaultUserLogRepository / DefaultUserLogQueryRepository — JPA(PostgreSQL Testcontainers)")
class DefaultUserLogRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;

    @Autowired
    private UserLogRepository repository;
    @Autowired
    private UserLogQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static CursorPagingRequest page(Long ci, int ps) {
        return new CursorPagingRequest(null, ci, null, ps);
    }

    private UserLog insert(int userSeq, ActivityType type, ActorType actor, String ip) {
        UserLog log = UserLog.createUserLog(userSeq, "KR", ip, type, actor);
        repository.insertUserLog(log);
        return log;
    }

    private void setRegisterDate(long seq, LocalDateTime at) {
        jdbc.update("UPDATE tb_user_log SET register_date = ? WHERE user_log_seq = ?", java.sql.Timestamp.valueOf(at), seq);
    }

    @Test
    @DisplayName("insertUserLog(identity Long·register_date DB DEFAULT·enum 이름) → findAll(register_date DESC·ps·ci 커서·사용자 격리·DTO 문자열) → exists 2종(isFirstLogin 반대 의미) / FK 위반은 맨 끝")
    void insertFindExists() {
        // 같은 컨테이너를 쓰는 다른 테스트가 커밋한 로그가 남아 있을 수 있어 시작 상태를 고정(롤백됨)
        jdbc.update("DELETE FROM tb_user_log WHERE user_seq IN (?, ?)", ADMIN, ANONYMOUS);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(queryRepository.existsLoginLog(ADMIN)).as("로그인 로그 없음 → true(첫 로그인)").isTrue();
        assertThat(queryRepository.existsChangePasswordLog(ADMIN)).isFalse();

        UserLog l1 = insert(ADMIN, ActivityType.LOGIN, ActorType.USER, "10.0.0.1");
        UserLog l2 = insert(ADMIN, ActivityType.CHANGE_PASSWORD, ActorType.ADMIN, "10.0.0.2");
        UserLog l3 = insert(ADMIN, ActivityType.LOGOUT, ActorType.USER, "10.0.0.3");
        UserLog other = insert(ANONYMOUS, ActivityType.LOGIN, ActorType.USER, "10.0.0.9");
        assertThat(l1.getUserLogSeq()).as("identity bigint → Long").isNotNull().isPositive();
        assertThat(l2.getUserLogSeq()).isGreaterThan(l1.getUserLogSeq());
        assertThat(l1.getRegisterDate()).as("insertable=false → DB DEFAULT, 메모리 값은 null").isNull();
        assertThat(jdbc.queryForObject("SELECT register_date FROM tb_user_log WHERE user_log_seq = ?", java.time.OffsetDateTime.class, l1.getUserLogSeq()).toInstant()).isAfterOrEqualTo(before.atZone(java.time.ZoneId.systemDefault()).toInstant());
        Map<String, Object> row = jdbc.queryForMap("SELECT user_seq, region, ip_address, activity_type, actor_type, register_date FROM tb_user_log WHERE user_log_seq = ?", l2.getUserLogSeq());
        assertThat(row.get("user_seq")).isEqualTo(ADMIN);
        assertThat(row.get("region")).isEqualTo("KR");
        assertThat(row.get("ip_address")).isEqualTo("10.0.0.2");
        assertThat(row.get("activity_type")).as("enum 이름 저장").isEqualTo("CHANGE_PASSWORD");
        assertThat(row.get("actor_type")).isEqualTo("ADMIN");
        assertThat(row.get("register_date")).isNotNull();

        // 정렬·커서 — 리스너는 초 절삭이라 같은 시각 → 날짜를 명시해 DESC 를 고정(l3 최신)
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        setRegisterDate(l1.getUserLogSeq(), t);
        setRegisterDate(l2.getUserLogSeq(), t.plusMinutes(1));
        setRegisterDate(l3.getUserLogSeq(), t.plusMinutes(2));
        List<UserLogData> all = queryRepository.findAll(ADMIN, page(null, 10));
        assertThat(all).extracting(UserLogData::getUserLogSeq).containsExactly(l3.getUserLogSeq(), l2.getUserLogSeq(), l1.getUserLogSeq());
        UserLogData first = all.get(0);
        assertThat(first.getUserSeq()).isEqualTo(ADMIN);
        assertThat(first.getRegion()).isEqualTo("KR");
        assertThat(first.getIpAddress()).isEqualTo("10.0.0.3");
        assertThat(first.getActivityType()).as("DTO 는 문자열(enum 이름)").isEqualTo("LOGOUT");
        assertThat(first.getActorType()).isEqualTo("USER");
        assertThat(first.getRegisterDate()).as("Instant(timestamptz) 왕복").isEqualTo(java.sql.Timestamp.valueOf(t.plusMinutes(2)).toInstant());
        assertThat(queryRepository.findAll(ADMIN, page(null, 2))).as("ps 제한").extracting(UserLogData::getUserLogSeq).containsExactly(l3.getUserLogSeq(), l2.getUserLogSeq());
        assertThat(queryRepository.findAll(ADMIN, page(l2.getUserLogSeq(), 10))).as("ci 미만만").extracting(UserLogData::getUserLogSeq).containsExactly(l1.getUserLogSeq());
        assertThat(queryRepository.findAll(ANONYMOUS, page(null, 10))).as("사용자 격리").extracting(UserLogData::getUserLogSeq).containsExactly(other.getUserLogSeq());
        assertThat(queryRepository.findAll(ADMIN, page(l1.getUserLogSeq(), 10))).isEmpty();

        // exists 계약
        assertThat(queryRepository.existsLoginLog(ADMIN)).as("로그인 로그 있음 → false").isFalse();
        assertThat(queryRepository.existsChangePasswordLog(ADMIN)).isTrue();
        assertThat(queryRepository.existsChangePasswordLog(ANONYMOUS)).isFalse();
        assertThat(queryRepository.existsLoginLog(ANONYMOUS)).isFalse();

        // fk_user_log_user_seq 위반 → PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> insert(999999, ActivityType.LOGIN, ActorType.USER, "10.0.0.0")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
