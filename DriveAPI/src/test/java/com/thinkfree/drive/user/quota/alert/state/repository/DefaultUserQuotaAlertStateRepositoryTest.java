package com.thinkfree.drive.user.quota.alert.state.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 UserQuotaAlertState 이관 회귀 — §1 v2: upsert 만 jOOQ(ON CONFLICT), 잠금 조회는 스칼라 투영 {@code @Lock(PESSIMISTIC_WRITE)} JPQL(엔티티 안 올림 — W7), IN 삭제는 JPQL({@link DefaultUserQuotaAlertStateRepository}).
 * tb_user_quota_alert_state 에 FK 없음(user_seq 임의 값, HASH 파티션). 트랜잭션 롤백.
 * 검증 포인트 = upsert INSERT 경로(register_date 엔티티 값 우선/비면 현재 시각, update_* 는 바인딩만 — 엔티티 write-back 없음), 충돌 경로(percent·update_* 갱신, register_date 유지, 반환 1), **잠금 조회가 같은 트랜잭션의 upsert 결과를 봄(엔티티를 컨텍스트에 안 올림)**, 없는 사용자 null, IN 삭제(빈·널 0).
 */
@DisplayName("DefaultUserQuotaAlertStateRepository — JPA 잠금 조회 + jOOQ upsert(PostgreSQL Testcontainers)")
class DefaultUserQuotaAlertStateRepositoryTest extends RepositoryTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;
    private static final int ACTOR = 920001;

    @Autowired
    private UserQuotaAlertStateRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private Integer lock(int userSeq) {
        return repository.lockLastThresholdPercentByUserSeq(userSeq);
    }

    private Map<String, Object> row(int userSeq) {
        return jdbc.queryForMap("SELECT last_threshold_percent, register_date, update_user_seq, update_date FROM tb_user_quota_alert_state WHERE user_seq = ?", userSeq);
    }

    private int count(int userSeq) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_quota_alert_state WHERE user_seq = ?", Integer.class, userSeq);
    }

    @Test
    @DisplayName("upsert(INSERT: register_date 엔티티 값 우선·update_* 는 DB 에 안 들어감 → 충돌: percent·update_* 갱신·register_date 유지·반환 1) · lock(같은 트랜잭션 upsert 결과를 봄, 없는 사용자 null) · deleteByUserSeqList(IN·빈·널 0)")
    void upsertLockDelete() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant occurred = Instant.parse("2026-01-01T01:01:01.123456Z");

        // INSERT 경로 — 비어 있는 감사 값은 현재 시각으로 바인딩(엔티티 write-back 없음 — W7), DB 에는 register_date 만(update_* 는 ON CONFLICT 분기 전용)
        UserQuotaAlertState first = UserQuotaAlertState.builder().userSeq(USER_A).lastThresholdPercent(0).build();
        assertThat(repository.upsert(first)).isEqualTo(1);
        assertThat(first.getRegisterDate()).as("write-back 없음").isNull();
        Instant firstRegisterDate = jdbc.queryForObject("SELECT register_date FROM tb_user_quota_alert_state WHERE user_seq = ?", OffsetDateTime.class, USER_A).toInstant();
        assertThat(firstRegisterDate).as("비면 현재 시각").isAfterOrEqualTo(before);
        Map<String, Object> inserted = row(USER_A);
        assertThat(inserted.get("last_threshold_percent")).as("pgjdbc 는 smallint 를 Integer 로").isEqualTo(0);
        assertThat(inserted.get("update_user_seq")).as("INSERT 경로는 update_* 미설정(구 XML 동형)").isNull();
        assertThat(inserted.get("update_date")).isNull();
        assertThat(count(USER_A)).isEqualTo(1);

        // 잠금 조회 — 같은 트랜잭션에서 방금 upsert 한 값
        assertThat(lock(USER_A)).isEqualTo(0);
        assertThat(lock(USER_B)).as("없는 사용자 null").isNull();

        // 충돌 경로 — percent·update_* 갱신(엔티티 값 우선), register_date 유지, 행 1개, 반환 1
        UserQuotaAlertState second = UserQuotaAlertState.builder().userSeq(USER_A).lastThresholdPercent(80)
                .registerDate(Instant.parse("2025-12-31T00:00:00Z")).updateUserSeq(ACTOR).updateDate(occurred).build();
        assertThat(repository.upsert(second)).isEqualTo(1);
        assertThat(count(USER_A)).isEqualTo(1);
        Map<String, Object> updated = row(USER_A);
        assertThat(updated.get("last_threshold_percent")).isEqualTo(80);
        assertThat(updated.get("update_user_seq")).isEqualTo(ACTOR);
        assertThat(((OffsetDateTime) jdbc.queryForObject("SELECT update_date FROM tb_user_quota_alert_state WHERE user_seq = ?", OffsetDateTime.class, USER_A)).toInstant()).isCloseTo(occurred, within(1, ChronoUnit.MICROS));
        assertThat(((OffsetDateTime) jdbc.queryForObject("SELECT register_date FROM tb_user_quota_alert_state WHERE user_seq = ?", OffsetDateTime.class, USER_A)).toInstant()).as("충돌 시 register_date 유지").isEqualTo(firstRegisterDate);

        // 재잠금 — 스칼라라 영속성 컨텍스트 캐시와 무관하게 DB 값(같은 트랜잭션의 upsert 결과)
        assertThat(lock(USER_A)).as("같은 트랜잭션 재조회가 DB 값").isEqualTo(80);

        // IN 삭제
        repository.upsert(UserQuotaAlertState.builder().userSeq(USER_B).lastThresholdPercent(90).build());
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(USER_A, 999999))).isEqualTo(1);
        assertThat(lock(USER_A)).isNull();
        assertThat(lock(USER_B)).as("다른 사용자 무영향").isNotNull();
        assertThat(repository.deleteByUserSeqList(List.of(USER_B))).isEqualTo(1);
        assertThat(count(USER_B)).isZero();
    }
}
