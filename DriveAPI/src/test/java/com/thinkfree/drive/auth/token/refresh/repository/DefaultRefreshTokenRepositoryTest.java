package com.thinkfree.drive.auth.token.refresh.repository;

import com.thinkfree.drive.auth.token.refresh.entity.RefreshToken;
import com.thinkfree.drive.common.RepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * RefreshToken 쓰기 리포 회귀 — {@link DefaultRefreshTokenRepository}: save·findBySessionId(재발급용 관리 엔티티 — 판정은 엔티티 isValid, RTR 은 변경 감지, W2 10-12)·delete 4종(JPQL @Modifying).
 * tb_refresh_token 에 FK 없음(user_seq 는 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = 엔티티 복원(restore) 5필드·Instant(timestamptz) 왕복, 관리 엔티티 updateRefreshToken → flush 가 token·만료 시각만 바꿈, deleteExpiredTokens 의 Instant 기준(만료만 삭제), IN 삭제·빈 목록 가드, session_id 유니크 위반은 맨 끝.
 */
@DisplayName("DefaultRefreshTokenRepository — JPA(PostgreSQL Testcontainers)")
class DefaultRefreshTokenRepositoryTest extends RepositoryTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired
    private RefreshTokenRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private static String session() {
        return UUID.randomUUID().toString();
    }

    private int count(int userSeq) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_refresh_token WHERE user_seq = ?", Integer.class, userSeq);
    }

    private boolean present(String sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_refresh_token WHERE session_id = ?", Integer.class, sessionId) > 0;
    }

    private Map<String, Object> row(String sessionId) {
        return jdbc.queryForMap("SELECT id, user_seq, refresh_token, expiration_date FROM tb_refresh_token WHERE session_id = ?", sessionId);
    }

    private static Instant expiration(Map<String, Object> row) {
        return ((Timestamp) row.get("expiration_date")).toInstant();
    }

    @Test
    @DisplayName("save(id identity·필드·Instant 왕복 — DB 확인) → findBySessionId 관리 엔티티 isValid(값 일치·만료 경계) / updateRefreshToken(변경 감지)은 refresh_token·expiration_date 만 session_id 기준 / session_id 유니크 위반은 맨 끝")
    void saveFindUpdate() {
        String s1 = session();
        String s2 = session();
        RefreshToken t1 = RefreshToken.createRefreshToken(USER_A, s1, "rt-1");
        RefreshToken t2 = RefreshToken.createRefreshToken(USER_B, s2, "rt-2");
        repository.save(t1);
        repository.save(t2);
        assertThat(t1.getId()).as("JPA persist — identity id 가 엔티티에 채워짐(2차 패스)").isNotNull();

        Map<String, Object> found = row(s1);
        assertThat(((Number) found.get("id")).longValue()).isPositive();
        assertThat(found.get("user_seq")).isEqualTo(USER_A);
        assertThat(found.get("refresh_token")).isEqualTo("rt-1");
        Instant expiration = expiration(found);
        assertThat(expiration).as("timestamptz 는 µs 정밀도(반올림)").isCloseTo(t1.getExpirationDate(), within(1, ChronoUnit.MICROS));
        assertThat(row(s2).get("user_seq")).isEqualTo(USER_B);

        // findBySessionId — 관리 엔티티, 판정은 엔티티 isValid(토큰 값 일치 + now 가 만료 시각을 넘지 않음)
        entityManager.clear();
        RefreshToken managed = repository.findBySessionId(s1);
        assertThat(entityManager.contains(managed)).as("관리 엔티티").isTrue();
        assertThat(managed.isValid("rt-1", Instant.now())).isTrue();
        assertThat(managed.isValid("rt-2", Instant.now())).as("토큰 값 불일치").isFalse();
        assertThat(managed.isValid(null, Instant.now())).as("null 토큰은 불일치").isFalse();
        assertThat(managed.isValid("rt-1", managed.getExpirationDate())).as("만료 시각과 같은 순간은 유효").isTrue();
        assertThat(managed.isValid("rt-1", managed.getExpirationDate().plusNanos(1000))).as("만료 뒤").isFalse();
        assertThat(repository.findBySessionId("no-such-session")).isNull();

        // RTR — 관리 엔티티를 고치면 flush(커밋) 때 변경 감지: 토큰·만료 시각 갱신, user_seq·id 유지
        managed.updateRefreshToken("rt-1b");
        assertThat(row(s1).get("refresh_token")).as("flush 전 — 메모리만").isEqualTo("rt-1");
        entityManager.flush();
        Map<String, Object> updated = row(s1);
        assertThat(updated.get("id")).isEqualTo(found.get("id"));
        assertThat(updated.get("user_seq")).isEqualTo(USER_A);
        assertThat(updated.get("refresh_token")).as("변경 감지로 DB 반영").isEqualTo("rt-1b");
        assertThat(expiration(updated)).isAfterOrEqualTo(expiration);
        assertThat(row(s2).get("refresh_token")).as("다른 세션 영향 없음").isEqualTo("rt-2");
        assertThat(count(USER_A)).isEqualTo(1);

        // uk_refresh_token_session_id 위반은 PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> repository.save(RefreshToken.createRefreshToken(USER_B, s1, "dup"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteBySessionId / deleteByUserSeq / deleteByUserSeqList(IN·빈·null 목록 0) / deleteExpiredTokens(Instant 기준, 만료된 것만)")
    void deletes() {
        String a1 = session();
        String a2 = session();
        String b1 = session();
        repository.save(RefreshToken.createRefreshToken(USER_A, a1, "a1"));
        repository.save(RefreshToken.createRefreshToken(USER_A, a2, "a2"));
        repository.save(RefreshToken.createRefreshToken(USER_B, b1, "b1"));

        repository.deleteBySessionId(a1);
        assertThat(present(a1)).isFalse();
        assertThat(count(USER_A)).isEqualTo(1);
        repository.deleteBySessionId("no-such-session");
        assertThat(count(USER_A)).isEqualTo(1);

        repository.deleteByUserSeq(USER_A);
        assertThat(count(USER_A)).isZero();
        assertThat(count(USER_B)).isEqualTo(1);

        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        repository.save(RefreshToken.createRefreshToken(USER_A, session(), "a3"));
        assertThat(repository.deleteByUserSeqList(List.of(USER_A, USER_B, 999999))).isEqualTo(2);
        assertThat(count(USER_A) + count(USER_B)).isZero();

        // deleteExpiredTokens — 만료 시각이 기준(Instant)보다 이른 행만
        String expired = session();
        String live = session();
        repository.save(RefreshToken.restore(null, USER_A, expired, "old", Instant.now().minus(1, ChronoUnit.DAYS)));
        repository.save(RefreshToken.createRefreshToken(USER_A, live, "new"));
        assertThat(present(expired)).isTrue();
        assertThat(repository.deleteExpiredTokens(Instant.now())).isEqualTo(1);
        assertThat(present(expired)).isFalse();
        assertThat(present(live)).isTrue();
        assertThat(repository.deleteExpiredTokens(Instant.now().minusSeconds(3600))).as("기준이 과거면 0").isZero();
        assertThat(repository.deleteExpiredTokens(Instant.now().plus(8, ChronoUnit.DAYS))).as("기준이 만료 뒤면 삭제").isEqualTo(1);
    }
}
