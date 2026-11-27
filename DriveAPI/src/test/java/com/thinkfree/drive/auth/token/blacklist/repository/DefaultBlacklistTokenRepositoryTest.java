package com.thinkfree.drive.auth.token.blacklist.repository;

import com.thinkfree.drive.auth.token.blacklist.entity.BlacklistToken;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.common.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 BlacklistToken 이관 회귀 — §1 v2 "JPA 우선", 구 XML 4 statement 전부 JPA(JdbcClient 0). 읽기({@link DefaultBlacklistTokenQueryRepository}: existsByTokenHash)·쓰기({@link DefaultBlacklistTokenRepository}: saveAndFlush·JPQL IN 삭제·만료 삭제).
 * tb_blacklist_token 에 FK 없음(user_seq 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = identity 키(bigint → Long) 주입·register_date 리스너(엔티티 값 우선)·Instant 왕복(µs), exists 참/거짓, IN 삭제(빈·널 목록 0, 다른 사용자 무영향), deleteExpiredTokens 기준 전후, token_hash 유니크 위반은 맨 끝.
 */
@DisplayName("DefaultBlacklistTokenRepository / DefaultBlacklistTokenQueryRepository — JPA(PostgreSQL Testcontainers)")
class DefaultBlacklistTokenRepositoryTest extends RepositoryTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired
    private BlacklistTokenRepository repository;
    @Autowired
    private BlacklistTokenQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static String hash(String seed) {
        StringBuilder sb = new StringBuilder(seed);
        while (sb.length() < 64) {
            sb.append('0');
        }
        return sb.substring(0, 64);
    }

    private static BlacklistToken token(int userSeq, String hash, Instant issued, Instant expires) {
        return BlacklistToken.builder().userSeq(userSeq).tokenHash(hash).issuedDate(issued).expirationDate(expires).build();
    }

    private boolean exists(String hash) {
        return queryRepository.existsByTokenHash(new BlacklistFindQuery(hash));
    }

    private int count(int userSeq) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_blacklist_token WHERE user_seq = ?", Integer.class, userSeq);
    }

    @Test
    @DisplayName("save(identity Long·register_date 리스너·엔티티 값 우선·Instant 왕복) → existsByTokenHash 참/거짓 / deleteByUserSeqList(IN·빈·널 0·다른 사용자 무영향) / token_hash 유니크 위반은 맨 끝")
    void saveExistsDelete() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant issued = Instant.parse("2026-01-01T01:01:01.123456Z");
        Instant expires = issued.plus(1, ChronoUnit.HOURS);

        BlacklistToken a1 = repository.save(token(USER_A, hash("a1"), issued, expires));
        assertThat(a1.getBlacklistTokenSeq()).as("identity 키(bigint) 주입").isNotNull().isPositive();
        assertThat(a1.getRegisterDate()).as("리스너").isNotNull().isAfterOrEqualTo(before);
        Instant fixedRegister = Instant.parse("2025-12-31T23:59:59Z");
        BlacklistToken b1 = repository.save(BlacklistToken.builder().userSeq(USER_B).tokenHash(hash("b1")).issuedDate(issued).expirationDate(expires).registerDate(fixedRegister).build());
        assertThat(b1.getRegisterDate()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixedRegister);

        OffsetDateTime dbIssued = jdbc.queryForObject("SELECT issued_date FROM tb_blacklist_token WHERE blacklist_token_seq = ?", OffsetDateTime.class, a1.getBlacklistTokenSeq());
        assertThat(dbIssued.toInstant()).isCloseTo(issued, within(1, ChronoUnit.MICROS));
        OffsetDateTime dbRegister = jdbc.queryForObject("SELECT register_date FROM tb_blacklist_token WHERE blacklist_token_seq = ?", OffsetDateTime.class, b1.getBlacklistTokenSeq());
        assertThat(dbRegister.toInstant()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixedRegister);

        assertThat(exists(hash("a1"))).isTrue();
        assertThat(exists(hash("b1"))).isTrue();
        assertThat(exists(hash("no-such"))).isFalse();

        // IN 삭제 — 빈·널 0, 목록의 사용자만
        repository.save(token(USER_A, hash("a2"), issued, expires));
        assertThat(count(USER_A)).isEqualTo(2);
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(count(USER_A)).isEqualTo(2);
        assertThat(repository.deleteByUserSeqList(List.of(USER_A, 999999))).isEqualTo(2);
        assertThat(count(USER_A)).isZero();
        assertThat(exists(hash("a1"))).as("JPQL 삭제 뒤 같은 트랜잭션 재확인은 DB 값").isFalse();
        assertThat(exists(hash("b1"))).as("다른 사용자 무영향").isTrue();
        assertThat(repository.deleteByUserSeqList(List.of(USER_B))).isEqualTo(1);
        assertThat(exists(hash("b1"))).isFalse();

        // uniq_token_hash 위반은 PG 트랜잭션 abort → 맨 끝
        repository.save(token(USER_A, hash("dup"), issued, expires));
        assertThatThrownBy(() -> repository.save(token(USER_B, hash("dup"), issued, expires))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteExpiredTokens(Instant 기준) — expiration_date < 기준인 행만, 기준이 과거면 0, 기준이 만료 뒤면 전부")
    void deleteExpiredTokens() {
        Instant now = Instant.now();
        repository.save(token(USER_A, hash("expired"), now.minusSeconds(7200), now.minusSeconds(60)));
        repository.save(token(USER_B, hash("live"), now, now.plusSeconds(3600)));

        assertThat(repository.deleteExpiredTokens(now.minusSeconds(86400))).as("기준이 과거면 0").isZero();
        assertThat(repository.deleteExpiredTokens(now)).isEqualTo(1);
        assertThat(exists(hash("expired"))).isFalse();
        assertThat(exists(hash("live"))).isTrue();
        assertThat(repository.deleteExpiredTokens(now)).as("재실행 0").isZero();
        assertThat(repository.deleteExpiredTokens(now.plus(1, ChronoUnit.DAYS))).as("기준이 만료 뒤면 삭제").isEqualTo(1);
        assertThat(exists(hash("live"))).isFalse();
    }

    @Test
    @DisplayName("findAllNotExpired — 만료 시각 > 기준인 (해시, 만료) 만, Redis 재적재용")
    void findAllNotExpired() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.save(token(USER_A, hash("gone"), now.minusSeconds(7200), now.minusSeconds(60)));
        repository.save(token(USER_B, hash("alive"), now, now.plusSeconds(3600)));

        var entries = queryRepository.findAllNotExpired(now).stream()
                .filter(e -> e.tokenHash().equals(hash("gone")) || e.tokenHash().equals(hash("alive")))
                .toList();
        assertThat(entries).extracting(e -> e.tokenHash()).containsExactly(hash("alive"));
        assertThat(entries.get(0).expirationDate()).isEqualTo(now.plusSeconds(3600));
    }
}
