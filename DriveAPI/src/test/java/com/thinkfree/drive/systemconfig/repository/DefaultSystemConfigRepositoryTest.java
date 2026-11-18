package com.thinkfree.drive.systemconfig.repository;

import com.thinkfree.drive.common.JdbcStatementCounter;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.stream.IntStream;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.within;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2단계 첫 이관(JdbcClient) 회귀. 시드(Flyway V1) = tenant 1 에 intellect_status·mfa_status·share_status·tenant_total_quota_byte(등록자 1) — office 키는 없음,
 * user 1(superadmin)·2(admin). 트랜잭션 롤백이라 시드 오염 없음.
 * 검증 포인트 = ON CONFLICT 키가 함수 유니크 (COALESCE(tenant_seq,0), setting_key) 와 맞는지(테넌트·글로벌 NULL 모두 2회 호출 → 행 1·값 갱신·register 보존),
 * IN 리스트 바인딩, Instant ↔ timestamptz 왕복, 글로벌 단건 optional.
 */
@DisplayName("DefaultSystemConfigRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultSystemConfigRepositoryTest extends RepositoryTest {

    private static final int TENANT = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private SystemConfigRepository repository;
    @Autowired
    private SystemConfigQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private SystemConfigJpaRepository jpaRepository;

    private int count(String key, Integer tenantSeq) {
        String sql = tenantSeq == null
                ? "SELECT COUNT(*) FROM tb_system_config WHERE tenant_seq IS NULL AND setting_key = ?"
                : "SELECT COUNT(*) FROM tb_system_config WHERE tenant_seq = " + tenantSeq + " AND setting_key = ?";
        Integer n = jdbc.queryForObject(sql, Integer.class, key);
        return n == null ? 0 : n;
    }

    private Instant registerDate(String key, int tenantSeq) {
        OffsetDateTime odt = jdbc.queryForObject("SELECT register_date FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", OffsetDateTime.class, tenantSeq, key);
        return odt == null ? null : odt.toInstant();
    }

    @Test
    @DisplayName("upsert — 새 행은 update_* NULL, 같은 (tenant, key) 2회 → 행 1개, value/update_* 갱신, register_* 보존")
    void upsert_insertsThenUpdates() {
        String key = "pg2_upsert_key";
        SystemConfig first = SystemConfig.createSystemConfig(TENANT, key, "v1", ADMIN);
        repository.upsert(first);
        assertThat(jdbc.queryForObject("SELECT update_user_seq FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", Integer.class, TENANT, key)).as("새 행 update_user_seq").isNull();
        assertThat(jdbc.queryForObject("SELECT update_date FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", java.time.OffsetDateTime.class, TENANT, key)).as("새 행 update_date").isNull();
        repository.upsert(SystemConfig.createSystemConfig(TENANT, key, "v2", SUPER_ADMIN));

        assertThat(count(key, TENANT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT value_text FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", String.class, TENANT, key)).isEqualTo("v2");
        assertThat(jdbc.queryForObject("SELECT update_user_seq FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", Integer.class, TENANT, key)).isEqualTo(SUPER_ADMIN);
        assertThat(jdbc.queryForObject("SELECT register_user_seq FROM tb_system_config WHERE tenant_seq = ? AND setting_key = ?", Integer.class, TENANT, key)).as("register_user_seq 는 첫 INSERT 값 유지").isEqualTo(ADMIN);
        assertThat(registerDate(key, TENANT)).as("register_date 는 첫 INSERT 값 유지(PG 는 micro 단위 반올림)")
                .isCloseTo(first.getRegisterDate(), within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("upsertBatch — 신규 2건 INSERT 후 같은 키 재호출 → 행 수 유지·값 갱신")
    void upsertBatch_insertsThenUpdates() {
        String k1 = "pg2_batch_a";
        String k2 = "pg2_batch_b";
        repository.upsertBatch(List.of(
                SystemConfig.createSystemConfig(TENANT, k1, "a1", ADMIN),
                SystemConfig.createSystemConfig(TENANT, k2, "b1", ADMIN)));
        repository.upsertBatch(List.of(
                SystemConfig.createSystemConfig(TENANT, k1, "a2", ADMIN),
                SystemConfig.createSystemConfig(TENANT, k2, "b2", ADMIN)));

        assertThat(count(k1, TENANT) + count(k2, TENANT)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT value_text FROM tb_system_config WHERE tenant_seq = ? AND setting_key IN (?, ?) ORDER BY setting_key", String.class, TENANT, k1, k2))
                .containsExactly("a2", "b2");
    }

    @Test
    @DisplayName("upsertBatch — 1001건은 1000행씩 batched 두 번(PreparedStatement·executeBatch 각 2회), 전부 들어간다")
    void upsertBatch_chunksBy1000() {
        int rows = JooqBatches.CHUNK_SIZE + 1;
        List<SystemConfig> list = IntStream.range(0, rows).mapToObj(i -> SystemConfig.createSystemConfig(TENANT, "pg2_chunk_" + i, "v" + i, ADMIN)).toList();
        JdbcStatementCounter counter = new JdbcStatementCounter(dataSource);
        SystemConfigRepository counted = new DefaultSystemConfigRepository(jpaRepository, DSL.using(counter, SQLDialect.POSTGRES));

        counted.upsertBatch(list);

        assertThat(counter.executeBatchCount()).as("1000행 + 1행").isEqualTo(2);
        assertThat(counter.addBatchCount()).isEqualTo(rows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_system_config WHERE tenant_seq = ? AND setting_key LIKE 'pg2_chunk_%'", Integer.class, TENANT)).isEqualTo(rows);
    }

    @Test
    @DisplayName("upsertBatch — 빈 목록은 no-op")
    void upsertBatch_emptyList() {
        repository.upsertBatch(List.of());
    }

    @Test
    @DisplayName("countByTenantAndKeys — IN 리스트 바인딩, 없는 키는 세지 않음, 빈 목록은 0")
    void countByTenantAndKeys() {
        assertThat(repository.countByTenantAndKeys(TENANT, List.of("intellect_status", "mfa_status", "pg2_no_such_key"))).isEqualTo(2);
        assertThat(repository.countByTenantAndKeys(TENANT + 999, List.of("intellect_status"))).isZero();
        assertThat(repository.countByTenantAndKeys(TENANT, List.of())).isZero();
    }

    @Test
    @DisplayName("Query 리포 findByTenantAndSettingConfigKeys — enum dbKey 로 조회, DTO(키·값), 없는 키 제외, 빈 목록은 빈 결과")
    void findByTenantAndSettingConfigKeys() {
        List<SystemConfigValueData> found = queryRepository.findByTenantAndSettingConfigKeys(TENANT,
                List.of(SystemConfigKey.INTELLECT_STATUS, SystemConfigKey.SHARE_STATUS, SystemConfigKey.OFFICE_DOMAIN));

        assertThat(found).as("시드에 없는 office_domain 은 결과에서 빠진다").extracting(SystemConfigValueData::getSettingKey)
                .containsExactlyInAnyOrder("intellect_status", "share_status");
        SystemConfigValueData share = found.stream().filter(c -> c.getSettingKey().equals("share_status")).findFirst().orElseThrow();
        assertThat(share.getValueText()).isEqualTo("true");
        assertThat(queryRepository.findByTenantAndSettingConfigKeys(TENANT + 999, List.of(SystemConfigKey.SHARE_STATUS))).isEmpty();
        assertThat(queryRepository.findByTenantAndSettingConfigKeys(TENANT, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Query 리포 — 같은 트랜잭션의 upsert(JdbcClient) 뒤 재조회도 DB 현재 값(생성자 식이라 1차 캐시 무관)")
    void find_afterUpsert_returnsCurrentDbValue() {
        repository.upsert(SystemConfig.createSystemConfig(TENANT, SystemConfigKey.SHARE_STATUS.getDbKey(), "false", ADMIN));
        assertThat(queryRepository.findByTenantAndSettingConfigKeys(TENANT, List.of(SystemConfigKey.SHARE_STATUS)).get(0).getValueText()).isEqualTo("false");
        repository.upsert(SystemConfig.createSystemConfig(TENANT, SystemConfigKey.SHARE_STATUS.getDbKey(), "true", ADMIN));
        assertThat(queryRepository.findByTenantAndSettingConfigKeys(TENANT, List.of(SystemConfigKey.SHARE_STATUS)).get(0).getValueText()).isEqualTo("true");
    }

    @Test
    @DisplayName("글로벌(tenant_seq NULL) — 없으면 null, upsertGlobal 2회 → 행 1개·값 갱신, Query 리포 findGlobalBySettingKey 매핑")
    void global_upsertAndFind() {
        String key = "pg2_global_key";
        assertThat(queryRepository.findGlobalBySettingKey(key)).isNull();

        repository.upsertGlobal(SystemConfig.createSystemConfig(null, key, "g1", SUPER_ADMIN));
        repository.upsertGlobal(SystemConfig.createSystemConfig(null, key, "g2", SUPER_ADMIN));

        assertThat(count(key, null)).isEqualTo(1);
        SystemConfigValueData found = queryRepository.findGlobalBySettingKey(key);
        assertThat(found).isNotNull();
        assertThat(found.getSettingKey()).isEqualTo(key);
        assertThat(found.getValueText()).isEqualTo("g2");
        assertThat(jdbc.queryForObject("SELECT update_date FROM tb_system_config WHERE tenant_seq IS NULL AND setting_key = ?", OffsetDateTime.class, key)).as("두 번째 upsert 가 갱신").isNotNull();
        assertThat(count(key, TENANT)).as("테넌트 행과 섞이지 않음").isZero();
    }
}
