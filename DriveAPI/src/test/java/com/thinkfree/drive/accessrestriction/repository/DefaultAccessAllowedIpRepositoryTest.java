package com.thinkfree.drive.accessrestriction.repository;

import com.thinkfree.drive.accessrestriction.data.AccessAllowedIpData;
import com.thinkfree.drive.accessrestriction.entity.AccessAllowedIp;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.user.enums.RoleType;
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
 * 2단계 AccessAllowedIp 이관 회귀 — §1 v2(AccessAllowedCountry §1-25 와 같은 꼴): 읽기({@link DefaultAccessAllowedIpQueryRepository}: JPQL 생성자 식 AccessAllowedIpData)·쓰기({@link DefaultAccessAllowedIpRepository}: JPQL 테넌트+역할 삭제 + **JDBC 배치 INSERT**).
 * tb_access_allowed_ip 에 FK 없음(tenant_seq·user_seq 임의 값). 트랜잭션 롤백.
 * 검증 포인트 = 배치 INSERT N건(role_type enum 이름·등록 감사 엔티티 값 우선·비면 write-back·키 미회수), findAllByTenantAndRole 정렬(ip_address, cidr_prefix 문자열 오름차순)·테넌트/역할 격리, deleteByTenantAndRole 이 역할 단위, 빈·널 no-op, 4컬럼 유니크 위반은 맨 끝.
 */
@DisplayName("DefaultAccessAllowedIpRepository / DefaultAccessAllowedIpQueryRepository — JPA + JDBC 배치 INSERT(PostgreSQL Testcontainers)")
class DefaultAccessAllowedIpRepositoryTest extends RepositoryTest {

    private static final int TENANT_A = 910001;
    private static final int TENANT_B = 910002;
    private static final int ADMIN = 920001;

    @Autowired
    private AccessAllowedIpRepository repository;
    @Autowired
    private AccessAllowedIpQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private List<String> cidrs(int tenantSeq, RoleType role) {
        return queryRepository.findAllByTenantAndRole(tenantSeq, role).stream().map(i -> i.getIpAddress() + "/" + i.getCidrPrefix()).toList();
    }

    private int count(int tenantSeq, RoleType role) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_access_allowed_ip WHERE tenant_seq = ? AND role_type = ?", Integer.class, tenantSeq, role.name());
    }

    @Test
    @DisplayName("insertAll(JDBC 배치 N건·role_type 이름·등록 감사 엔티티 값 우선·키 미회수) → findAllByTenantAndRole(정렬·테넌트/역할 격리·DTO) → deleteByTenantAndRole(역할 단위 전체 교체) / 빈·널 no-op / 유니크 위반은 맨 끝")
    void replaceFlow() {
        List<AccessAllowedIp> userIps = List.of(
                AccessAllowedIp.create(TENANT_A, RoleType.USER, "203.0.113.0", 24, ADMIN),
                AccessAllowedIp.create(TENANT_A, RoleType.USER, "10.0.0.1", 32, ADMIN),
                AccessAllowedIp.create(TENANT_A, RoleType.USER, "10.0.0.0", 8, ADMIN));
        Instant explicitDate = userIps.get(0).getRegisterDate();
        repository.insertAll(userIps);
        repository.insertAll(List.of(AccessAllowedIp.create(TENANT_A, RoleType.ADMIN, "192.168.1.0", 24, ADMIN)));
        repository.insertAll(List.of(AccessAllowedIp.create(TENANT_B, RoleType.USER, "10.0.0.1", 32, ADMIN)));
        assertThat(userIps).allSatisfy(e -> assertThat(e.getIpSeq()).as("생성 키는 돌려받지 않음(구 XML 동일)").isNull());
        assertThat(userIps.get(0).getRegisterDate()).as("create 가 넣은 현재 시각 유지(엔티티 값 우선)").isEqualTo(explicitDate);
        assertThat(count(TENANT_A, RoleType.USER)).isEqualTo(3);
        assertThat(count(TENANT_A, RoleType.ADMIN)).isEqualTo(1);
        assertThat(count(TENANT_B, RoleType.USER)).isEqualTo(1);

        // 정렬 = ip_address 문자열, cidr_prefix 오름차순(구 XML ORDER BY 동형)
        assertThat(cidrs(TENANT_A, RoleType.USER)).containsExactly("10.0.0.0/8", "10.0.0.1/32", "203.0.113.0/24");
        assertThat(cidrs(TENANT_A, RoleType.ADMIN)).containsExactly("192.168.1.0/24");
        assertThat(cidrs(TENANT_B, RoleType.USER)).containsExactly("10.0.0.1/32");
        assertThat(cidrs(TENANT_A, RoleType.SUPER_ADMIN)).isEmpty();
        assertThat(cidrs(999999, RoleType.USER)).isEmpty();
        AccessAllowedIpData found = queryRepository.findAllByTenantAndRole(TENANT_A, RoleType.USER).get(1);
        assertThat(found.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(found.getCidrPrefix()).isEqualTo(32);
        // DTO 는 (ip, cidr) 만 — 나머지 컬럼 왕복은 DB 로 확인
        Map<String, Object> row = jdbc.queryForMap("SELECT ip_seq, role_type, ip_address, cidr_prefix, register_user_seq, register_date FROM tb_access_allowed_ip WHERE tenant_seq = ? AND role_type = ? AND ip_address = ? AND cidr_prefix = ?", TENANT_A, "USER", "10.0.0.1", 32);
        assertThat((Integer) row.get("ip_seq")).as("identity 키").isNotNull().isPositive();
        assertThat(row.get("role_type")).as("enum 이름 저장").isEqualTo("USER");
        assertThat(row.get("register_user_seq")).isEqualTo(ADMIN);
        // 비교 기준은 같은 엔티티(10.0.0.1/32 = userIps.get(1)) — create 마다 Instant.now() 를 따로 잡으므로 get(0) 값과 비교하면 호출 간격만큼 어긋남(플래키)
        assertThat(((java.sql.Timestamp) row.get("register_date")).toInstant()).as("timestamptz µs 반올림").isCloseTo(userIps.get(1).getRegisterDate(), within(1, ChronoUnit.MICROS));

        // 역할 단위 전체 교체 — USER 만 삭제, ADMIN·다른 테넌트 무영향
        repository.deleteByTenantAndRole(TENANT_A, RoleType.USER);
        assertThat(count(TENANT_A, RoleType.USER)).isZero();
        assertThat(cidrs(TENANT_A, RoleType.USER)).as("JPQL 삭제 뒤 같은 트랜잭션 재조회는 DB 값").isEmpty();
        assertThat(cidrs(TENANT_A, RoleType.ADMIN)).containsExactly("192.168.1.0/24");
        assertThat(cidrs(TENANT_B, RoleType.USER)).containsExactly("10.0.0.1/32");
        repository.insertAll(List.of(AccessAllowedIp.create(TENANT_A, RoleType.USER, "172.16.0.0", 12, ADMIN)));
        assertThat(cidrs(TENANT_A, RoleType.USER)).containsExactly("172.16.0.0/12");
        repository.deleteByTenantAndRole(999999, RoleType.USER);
        assertThat(cidrs(TENANT_A, RoleType.USER)).containsExactly("172.16.0.0/12");

        // 빈·널 목록 no-op
        repository.insertAll(List.of());
        repository.insertAll(null);
        assertThat(count(TENANT_A, RoleType.USER)).isEqualTo(1);

        // uniq_tenant_role_type_ip 위반 → PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> repository.insertAll(List.of(AccessAllowedIp.create(TENANT_A, RoleType.USER, "172.16.0.0", 12, ADMIN))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insertAll — 등록 감사가 비어 있으면 현재 시각(초 절삭) write-back(리스너·인터셉터 규칙), register_user_seq 는 엔티티 값")
    void auditFallback() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AccessAllowedIp e = AccessAllowedIp.create(TENANT_A, RoleType.USER, "198.51.100.0", 24, ADMIN);
        java.lang.reflect.Field f = com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity.class.getDeclaredField("registerDate");
        f.setAccessible(true);
        f.set(e, null);
        repository.insertAll(List.of(e));
        assertThat(e.getRegisterDate()).as("비면 JdbcAudit.now() write-back").isNotNull().isAfterOrEqualTo(before);
        assertThat(e.getRegisterUserSeq()).isEqualTo(ADMIN);
        OffsetDateTime db = jdbc.queryForObject("SELECT register_date FROM tb_access_allowed_ip WHERE tenant_seq = ? AND role_type = ? AND ip_address = ?", OffsetDateTime.class, TENANT_A, "USER", "198.51.100.0");
        assertThat(db.toInstant()).isEqualTo(e.getRegisterDate());
    }
}
