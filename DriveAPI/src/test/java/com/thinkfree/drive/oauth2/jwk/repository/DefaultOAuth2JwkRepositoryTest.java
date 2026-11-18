package com.thinkfree.drive.oauth2.jwk.repository;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;
import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;
import com.thinkfree.drive.oauth2.jwk.service.OAuth2JwkService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 OAuth2Jwk 이관 회귀 — §1 v2 "JPA 우선", 구 XML 4 statement 전부 JPA(JdbcClient 0). **할당형 PK(kid) 첫 적용**: 읽기({@link DefaultOAuth2JwkQueryRepository}: JPQL 생성자 식 findAllValid — OAuth2JwkData)·쓰기({@link DefaultOAuth2JwkRepository}: persist·JPQL 강등·만료 삭제).
 * 컨텍스트 기동 때 {@code OAuth2KeyManager.init} 이 같은 리포로 현역 키 1개를 이미 넣어 둔다(커밋됨) → 단언은 이 테스트가 만든 kid(접두사 {@code pgjwk-}) 기준. 트랜잭션 롤백.
 * 검증 포인트 = persist INSERT(register_date 리스너·expiration NULL·isSigning), findAllValid 의 유효 조건(NULL 또는 미래)·정렬(register_date DESC, kid DESC — JdbcTemplate 로 날짜 고정 삽입), expireSigningKeys 가 NULL 행만, deleteExpired 가 과거 만료만, 중복 kid 는 맨 끝.
 */
@DisplayName("DefaultOAuth2JwkRepository / DefaultOAuth2JwkQueryRepository — JPA 할당형 PK(PostgreSQL Testcontainers)")
class DefaultOAuth2JwkRepositoryTest extends RepositoryTest {

    @Autowired
    private OAuth2JwkRepository repository;
    @Autowired
    private OAuth2JwkQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OAuth2JwkService jwkService;

    private static OAuth2Jwk key(String kid) throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID(kid).generate();
        return OAuth2Jwk.of(rsaKey);
    }

    private void insertRaw(String kid, Instant registerDate, Instant expirationDate) {
        jdbc.update("INSERT INTO tb_oauth2_jwk (kid, jwk_json, register_date, expiration_date) VALUES (?, '{\"kid\":\"' || ? || '\"}', ?, ?)",
                kid, kid, OffsetDateTime.ofInstant(registerDate, ZoneOffset.UTC),
                expirationDate == null ? null : OffsetDateTime.ofInstant(expirationDate, ZoneOffset.UTC));
    }

    private List<String> validKids() {
        return queryRepository.findAllValid().stream().map(OAuth2JwkData::getKid).filter(k -> k.startsWith("pgjwk-")).toList();
    }

    @Test
    @DisplayName("save(persist: register_date 리스너·expiration NULL·isSigning·jwk_json 원본) → findAllValid(현역 + 미래 만료만, register_date DESC·kid DESC, DTO) / 중복 kid 는 맨 끝")
    void saveAndFindAllValid() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2Jwk fresh = key("pgjwk-fresh");
        repository.save(fresh);
        assertThat(entityManager.contains(fresh)).as("Persistable — save 가 persist(merge 면 다른 인스턴스가 관리됨)").isTrue();
        entityManager.flush();   // 할당형 PK 라 INSERT 는 flush·커밋 때 — JdbcTemplate 확인 전에 앞당김
        assertThat(fresh.getRegisterDate()).as("리스너").isNotNull().isAfterOrEqualTo(before);
        assertThat(fresh.getExpirationDate()).isNull();
        assertThat(fresh.isSigning()).isTrue();
        assertThat(jdbc.queryForObject("SELECT jwk_json FROM tb_oauth2_jwk WHERE kid = ?", String.class, "pgjwk-fresh")).contains("\"kid\":\"pgjwk-fresh\"").contains("\"d\":");
        assertThat(jdbc.queryForObject("SELECT expiration_date FROM tb_oauth2_jwk WHERE kid = ?", OffsetDateTime.class, "pgjwk-fresh")).isNull();

        // 정렬·유효 조건 — 날짜를 고정한 원시 행: 같은 register_date 는 kid DESC, 미래 만료는 포함, 과거 만료는 제외
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        insertRaw("pgjwk-a", t, null);
        insertRaw("pgjwk-b", t, Instant.now().plus(1, ChronoUnit.HOURS));
        insertRaw("pgjwk-old", t.minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.HOURS));
        insertRaw("pgjwk-expired", t, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(validKids()).containsExactly("pgjwk-fresh", "pgjwk-b", "pgjwk-a", "pgjwk-old");

        OAuth2JwkData found = queryRepository.findAllValid().stream().filter(j -> j.getKid().equals("pgjwk-fresh")).findFirst().orElseThrow();
        assertThat(found.getJwkJson()).isEqualTo(fresh.getJwkJson());
        assertThat(jdbc.queryForObject("SELECT register_date FROM tb_oauth2_jwk WHERE kid = ?", OffsetDateTime.class, "pgjwk-fresh").toInstant()).as("DTO 에 없는 register_date 는 DB 로 — timestamptz µs 반올림").isCloseTo(fresh.getRegisterDate(), within(1, ChronoUnit.MICROS));
        assertThat(found.isSigning()).isTrue();
        assertThat(queryRepository.findAllValid().stream().filter(j -> j.getKid().equals("pgjwk-b")).findFirst().orElseThrow().isSigning()).isFalse();

        // 할당형 PK 중복(같은 영속성 컨텍스트에 이미 있는 kid) → persist 에서 거부 → 맨 끝
        assertThatThrownBy(() -> repository.save(key("pgjwk-fresh"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("expireSigningKeys(NULL 행만 강등, 건수) → 강등 키는 유예기간 동안 유효 목록에 남음 / deleteExpired(과거 만료만 삭제, 건수)")
    void rotateAndDeleteExpired() throws Exception {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        insertRaw("pgjwk-signing-1", t, null);
        insertRaw("pgjwk-signing-2", t.plus(1, ChronoUnit.HOURS), null);
        insertRaw("pgjwk-demoted", t, Instant.now().plus(1, ChronoUnit.HOURS));
        insertRaw("pgjwk-dead", t, Instant.now().minus(1, ChronoUnit.MINUTES));
        int signingBefore = jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE expiration_date IS NULL", Integer.class);

        Instant grace = Instant.now().plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        assertThat(repository.expireSigningKeys(grace)).as("NULL 행 전부(기동 때 들어간 현역 키 포함)").isEqualTo(signingBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE expiration_date IS NULL", Integer.class)).isZero();
        OffsetDateTime db = jdbc.queryForObject("SELECT expiration_date FROM tb_oauth2_jwk WHERE kid = ?", OffsetDateTime.class, "pgjwk-signing-1");
        assertThat(db.toInstant()).isEqualTo(grace);
        assertThat(jdbc.queryForObject("SELECT expiration_date FROM tb_oauth2_jwk WHERE kid = ?", OffsetDateTime.class, "pgjwk-dead").toInstant())
                .as("이미 만료된 행은 손대지 않음").isBefore(OffsetDateTime.now().toInstant());
        assertThat(validKids()).as("강등돼도 유예기간 동안은 유효").containsExactly("pgjwk-signing-2", "pgjwk-signing-1", "pgjwk-demoted");
        assertThat(repository.expireSigningKeys(grace)).as("재실행 0").isZero();

        // 새 현역 키 저장(rotate 2단계) → 최신 우선이라 맨 앞
        repository.save(key("pgjwk-rotated"));
        entityManager.flush();
        assertThat(validKids().get(0)).isEqualTo("pgjwk-rotated");

        // deleteExpired — 과거 만료만
        assertThat(repository.deleteExpired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE kid = 'pgjwk-dead'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE kid LIKE 'pgjwk-%'", Integer.class)).isEqualTo(4);
        assertThat(repository.deleteExpired()).as("재실행 0").isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("flush 없는 save — 서비스 트랜잭션 커밋 때 INSERT, 중복 kid 는 커밋에서 DataAccessException(OAuth2KeyManager 의 catch 대상)으로 변환")
    void save_duplicateKid_failsAtCommitAsDataAccessException() throws Exception {
        String kid = "pgjwk-commit-dup";
        try {
            jwkService.save(key(kid));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE kid = ?", Integer.class, kid)).as("커밋됨").isEqualTo(1);

            assertThatThrownBy(() -> jwkService.save(key(kid))).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_jwk WHERE kid = ?", Integer.class, kid)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM tb_oauth2_jwk WHERE kid = ?", kid);   // 트랜잭션 밖이라 직접 정리
        }
    }
}
