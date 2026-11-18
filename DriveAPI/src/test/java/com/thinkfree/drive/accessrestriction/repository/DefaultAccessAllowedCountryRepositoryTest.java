package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.entity.AccessAllowedCountry;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 2단계 AccessAllowedCountry 이관 회귀 — §1 v2: 읽기({@link DefaultAccessAllowedCountryQueryRepository}: JPQL 국가 코드 스칼라)·쓰기({@link DefaultAccessAllowedCountryRepository}: JPQL 테넌트 삭제 + **JDBC 배치 INSERT**).
 * tb_access_allowed_country 에 FK 없음(tenant_seq·user_seq 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = 배치 INSERT N건(char(2) 왕복·등록 감사 엔티티 값 우선·비면 현재 시각 write-back·생성 키 미회수), findAllCountryCodes 정렬(코드 오름차순)·테넌트 격리, deleteByTenant 전체 교체 흐름, 빈·널 목록 no-op, (tenant, code) 유니크 위반은 맨 끝.
 */
@DisplayName("DefaultAccessAllowedCountryRepository / DefaultAccessAllowedCountryQueryRepository — JPA + JDBC 배치 INSERT(PostgreSQL Testcontainers)")
class DefaultAccessAllowedCountryRepositoryTest extends RepositoryTest {

    private static final int TENANT_A = 910001;
    private static final int TENANT_B = 910002;
    private static final int ADMIN = 920001;

    @Autowired
    private AccessAllowedCountryRepository repository;
    @Autowired
    private AccessAllowedCountryQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private List<String> codes(int tenantSeq) {
        return queryRepository.findAllCountryCodes(tenantSeq);
    }

    private int count(int tenantSeq) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_access_allowed_country WHERE tenant_seq = ?", Integer.class, tenantSeq);
    }

    @Test
    @DisplayName("insertAll(JDBC 배치 N건·char(2)·등록 감사 엔티티 값 우선/비면 write-back·키 미회수) → findAllCountryCodes(코드 오름차순·테넌트 격리) → deleteByTenant(전체 교체) / 빈·널 목록 no-op / 유니크 위반은 맨 끝")
    void replaceFlow() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<AccessAllowedCountry> a = List.of(
                AccessAllowedCountry.create(TENANT_A, "US", ADMIN),
                AccessAllowedCountry.create(TENANT_A, "KR", ADMIN),
                AccessAllowedCountry.create(TENANT_A, "JP", ADMIN));
        Instant explicitDate = a.get(0).getRegisterDate();
        repository.insertAll(a);
        repository.insertAll(List.of(AccessAllowedCountry.create(TENANT_B, "KR", ADMIN)));
        assertThat(a).allSatisfy(e -> assertThat(e.getCountrySeq()).as("생성 키는 돌려받지 않음(구 XML 동일)").isNull());
        assertThat(a.get(0).getRegisterDate()).as("create 가 넣은 현재 시각 유지(엔티티 값 우선)").isEqualTo(explicitDate);
        assertThat(a.get(0).getRegisterUserSeq()).isEqualTo(ADMIN);
        assertThat(count(TENANT_A)).isEqualTo(3);
        assertThat(count(TENANT_B)).isEqualTo(1);

        assertThat(codes(TENANT_A)).containsExactly("JP", "KR", "US");
        assertThat(codes(TENANT_B)).containsExactly("KR");
        assertThat(codes(999999)).isEmpty();
        // 조회는 국가 코드 스칼라 — 나머지 컬럼 왕복은 DB 로 확인
        Map<String, Object> row = jdbc.queryForMap("SELECT country_seq, country_iso_code, register_user_seq, register_date FROM tb_access_allowed_country WHERE tenant_seq = ? AND country_iso_code = ?", TENANT_A, "KR");
        assertThat((Integer) row.get("country_seq")).as("identity 키").isNotNull().isPositive();
        assertThat(row.get("country_iso_code")).isEqualTo("KR");
        assertThat(row.get("register_user_seq")).isEqualTo(ADMIN);
        // 비교 기준은 같은 엔티티(KR = a.get(1)) — create 마다 Instant.now() 를 따로 잡으므로 get(0)(US) 값과 비교하면 부하 시 수백 µs 벌어진다
        assertThat(((java.sql.Timestamp) row.get("register_date")).toInstant()).as("timestamptz µs 반올림").isCloseTo(a.get(1).getRegisterDate(), within(1, ChronoUnit.MICROS));

        // 전체 교체 — 삭제 → 새 목록 배치 INSERT, 다른 테넌트 무영향
        repository.deleteByTenant(TENANT_A);
        assertThat(count(TENANT_A)).isZero();
        assertThat(codes(TENANT_A)).as("JPQL 삭제 뒤 같은 트랜잭션 재조회는 DB 값").isEmpty();
        assertThat(codes(TENANT_B)).containsExactly("KR");
        repository.insertAll(List.of(AccessAllowedCountry.create(TENANT_A, "DE", ADMIN)));
        assertThat(codes(TENANT_A)).containsExactly("DE");
        repository.deleteByTenant(999999);
        assertThat(codes(TENANT_A)).containsExactly("DE");

        // 빈·널 목록 no-op
        repository.insertAll(List.of());
        repository.insertAll(null);
        assertThat(count(TENANT_A)).isEqualTo(1);

        // uniq_tenant_country 위반(배치 안 중복) → PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> repository.insertAll(List.of(AccessAllowedCountry.create(TENANT_A, "DE", ADMIN))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insertAll — 등록 감사가 비어 있으면 현재 시각(초 절삭) write-back(리스너·인터셉터 규칙), register_user_seq 는 엔티티 값")
    void auditFallback() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AccessAllowedCountry e = AccessAllowedCountry.create(TENANT_A, "FR", ADMIN);
        java.lang.reflect.Field f = com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity.class.getDeclaredField("registerDate");
        f.setAccessible(true);
        f.set(e, null);
        repository.insertAll(List.of(e));
        assertThat(e.getRegisterDate()).as("비면 JdbcAudit.now() write-back").isNotNull().isAfterOrEqualTo(before);
        assertThat(e.getRegisterUserSeq()).isEqualTo(ADMIN);
        OffsetDateTime db = jdbc.queryForObject("SELECT register_date FROM tb_access_allowed_country WHERE tenant_seq = ? AND country_iso_code = ?", OffsetDateTime.class, TENANT_A, "FR");
        assertThat(db.toInstant()).isEqualTo(e.getRegisterDate());
    }
}
