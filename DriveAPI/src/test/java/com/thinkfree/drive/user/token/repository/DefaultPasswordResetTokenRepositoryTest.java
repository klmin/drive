package com.thinkfree.drive.user.token.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.user.token.entity.PasswordResetToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 PasswordResetToken 이관 회귀 — §1 v2 "JPA 우선", 구 XML 5 statement 전부 JPA(JdbcClient 0, TenantAccessToken §1-19 와 같은 꼴). 쓰기 리포 하나({@link DefaultPasswordResetTokenRepository}: saveAndFlush·파생 조회·JPQL 삭제 3종).
 * tb_password_reset_token 에 FK 없음(user_seq 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = identity 키 주입·register_date 리스너(비면 현재 시각, 엔티티 값 우선)·Instant(timestamptz) 왕복(µs 반올림), 없는 해시 null, deleteByUserSeq 가 같은 사용자만, deleteByTokenHash 뒤 같은 트랜잭션 재조회가 null(JPQL 삭제 + 관리 인스턴스 잔존 무해),
 * deleteExpiredTokens 의 기준 전후(만료만), JPQL 삭제가 readOnly 거부 없이 동작, token_hash 유니크 위반은 맨 끝(PG tx abort).
 */
@DisplayName("DefaultPasswordResetTokenRepository — JPA(PostgreSQL Testcontainers)")
class DefaultPasswordResetTokenRepositoryTest extends RepositoryTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired
    private PasswordResetTokenRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private static String hash(String seed) {
        // token_hash 는 char(64) — SHA-256 hex 와 같은 길이로 채움
        StringBuilder sb = new StringBuilder(seed);
        while (sb.length() < 64) {
            sb.append('0');
        }
        return sb.substring(0, 64);
    }

    private int countByUser(int userSeq) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_password_reset_token WHERE user_seq = ?", Integer.class, userSeq);
    }

    @Test
    @DisplayName("save(identity 키·register_date 리스너, 엔티티 값 우선, Instant 왕복) → findByTokenHash / 없는 해시 null / deleteByUserSeq 는 같은 사용자만 / deleteByTokenHash 뒤 재조회 null / token_hash 유니크 위반은 맨 끝")
    void saveFindDelete() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant issued = Instant.parse("2026-01-01T01:01:01.123456Z");
        Instant expires = issued.plus(30, ChronoUnit.MINUTES);

        PasswordResetToken a1 = PasswordResetToken.of(USER_A, hash("a1"), issued, expires);
        repository.save(a1);
        assertThat(a1.getPasswordResetTokenSeq()).as("identity 키 주입").isNotNull().isPositive();
        assertThat(a1.getRegisterDate()).as("리스너가 register_date 를 채움(초 절삭 현재 시각)").isNotNull().isAfterOrEqualTo(before);

        // 엔티티 값 우선 — register_date 를 명시하면 리스너가 덮지 않음
        Instant fixedRegister = Instant.parse("2025-12-31T23:59:59Z");
        PasswordResetToken b1 = PasswordResetToken.builder()
                .userSeq(USER_B).tokenHash(hash("b1"))
                .issuedDate(issued).expirationDate(expires).registerDate(fixedRegister)
                .build();
        repository.save(b1);
        assertThat(b1.getRegisterDate()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixedRegister);

        // DB 왕복(JdbcTemplate 으로 직접) — timestamptz 는 µs 정밀도
        OffsetDateTime dbIssued = jdbc.queryForObject("SELECT issued_date FROM tb_password_reset_token WHERE password_reset_token_seq = ?", OffsetDateTime.class, a1.getPasswordResetTokenSeq());
        assertThat(dbIssued.toInstant()).isCloseTo(issued, within(1, ChronoUnit.MICROS));
        OffsetDateTime dbRegister = jdbc.queryForObject("SELECT register_date FROM tb_password_reset_token WHERE password_reset_token_seq = ?", OffsetDateTime.class, b1.getPasswordResetTokenSeq());
        assertThat(dbRegister.toInstant()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixedRegister);

        // findByTokenHash — 6 컬럼 복원, 없는 해시 null
        PasswordResetToken found = repository.findByTokenHash(hash("a1"));
        assertThat(found).isNotNull();
        assertThat(found.getPasswordResetTokenSeq()).isEqualTo(a1.getPasswordResetTokenSeq());
        assertThat(found.getUserSeq()).isEqualTo(USER_A);
        assertThat(found.getTokenHash()).isEqualTo(hash("a1"));
        assertThat(found.getIssuedDate()).isCloseTo(issued, within(1, ChronoUnit.MICROS));
        assertThat(found.getExpirationDate()).isCloseTo(expires, within(1, ChronoUnit.MICROS));
        assertThat(found.getRegisterDate()).isCloseTo(a1.getRegisterDate(), within(1, ChronoUnit.MICROS));
        assertThat(repository.findByTokenHash(hash("no-such"))).isNull();

        // deleteByUserSeq — 같은 사용자 재요청 흐름(기존 삭제 → 새로 저장), 다른 사용자 무영향
        repository.save(PasswordResetToken.of(USER_A, hash("a2"), issued, expires));
        assertThat(countByUser(USER_A)).isEqualTo(2);
        repository.deleteByUserSeq(USER_A);
        assertThat(countByUser(USER_A)).isZero();
        assertThat(repository.findByTokenHash(hash("a1"))).as("JPQL 삭제 뒤 같은 트랜잭션 재조회는 DB 값(null)").isNull();
        assertThat(repository.findByTokenHash(hash("a2"))).isNull();
        assertThat(countByUser(USER_B)).isEqualTo(1);
        repository.deleteByUserSeq(999999);
        assertThat(countByUser(USER_B)).isEqualTo(1);
        PasswordResetToken a3 = PasswordResetToken.of(USER_A, hash("a3"), issued, expires);
        repository.save(a3);
        assertThat(a3.getPasswordResetTokenSeq()).isGreaterThan(a1.getPasswordResetTokenSeq());
        assertThat(repository.findByTokenHash(hash("a3"))).isNotNull();

        // deleteByTokenHash — 변경 완료 흐름(조회 → 삭제), 재조회 null, 다른 행 무영향
        repository.deleteByTokenHash(hash("a3"));
        assertThat(repository.findByTokenHash(hash("a3"))).isNull();
        assertThat(countByUser(USER_A)).isZero();
        repository.deleteByTokenHash(hash("no-such"));
        assertThat(repository.findByTokenHash(hash("b1"))).isNotNull();

        // uniq_password_reset_token_hash 위반은 PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> repository.save(PasswordResetToken.of(USER_A, hash("b1"), issued, expires)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteExpiredTokens(Instant 기준) — expiration_date < 기준인 행만, 기준이 과거면 0, 기준이 만료 뒤면 전부")
    void deleteExpiredTokens() {
        Instant now = Instant.now();
        repository.save(PasswordResetToken.of(USER_A, hash("expired"), now.minusSeconds(600), now.minusSeconds(60)));
        repository.save(PasswordResetToken.of(USER_B, hash("live"), now, now.plusSeconds(600)));

        assertThat(repository.deleteExpiredTokens(now.minusSeconds(3600))).as("기준이 과거면 0").isZero();
        assertThat(repository.deleteExpiredTokens(now)).isEqualTo(1);
        assertThat(repository.findByTokenHash(hash("expired"))).isNull();
        assertThat(repository.findByTokenHash(hash("live"))).isNotNull();
        assertThat(repository.deleteExpiredTokens(now)).as("재실행 0").isZero();
        assertThat(repository.deleteExpiredTokens(now.plus(1, ChronoUnit.DAYS))).as("기준이 만료 뒤면 삭제").isEqualTo(1);
        assertThat(repository.findByTokenHash(hash("live"))).isNull();
    }
}
