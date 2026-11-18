package com.thinkfree.drive.tenant.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.query.TenantListQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Tenant 리포 회귀. 시드 = tenant 1('기본 테넌트', SELF_MANAGED, MULTI, ACTIVE, LOCAL, domain NULL, 등록자 1 superadmin, update NULL),
 * user 1(superadmin)·2(admin)·3(anonymous). 트랜잭션 롤백.
 * 검증 포인트 = FULL(조인 16컬럼)/BASIC(8컬럼) 생성자 식·enum 왕복, identity 생성 키 엔티티 주입, 동적 조건(커서·자기 제외),
 * findBySubdomain 중복 시 최솟값, LIMIT 2 / COUNT, 쓰기 리포 로드(관리 엔티티) + 변경 감지의 갱신·보존 컬럼(JdbcTemplate 로 DB 값 확인), 감사(Auditing), 유니크·CHECK 전파.
 */
@DisplayName("DefaultTenantRepository — JPA(PostgreSQL Testcontainers)")
class DefaultTenantRepositoryTest extends RepositoryTest {

    private static final int SEED_TENANT = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private TenantRepository repository;
    @Autowired
    private TenantQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    @BeforeEach
    void loginAsSuperAdmin() {
        loginAs(SUPER_ADMIN);
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

    /** converter.toSaveTenant 형태(SELF_MANAGED·MULTI·ACTIVE·LOCAL, 감사 없음). */
    private static Tenant newTenant(String name, String domain, String subdomain, Integer baseDomainSeq) {
        return newTenant(name, domain, subdomain, baseDomainSeq, TenantType.SELF_MANAGED, TenantUserMode.MULTI);
    }

    private static Tenant newTenant(String name, String domain, String subdomain, Integer baseDomainSeq, TenantType type, TenantUserMode userMode) {
        return Tenant.builder()
                .tenantName(name).domain(domain).subdomain(subdomain).baseDomainSeq(baseDomainSeq)
                .tenantType(type).userMode(userMode).status(TenantStatus.ACTIVE).loginType(LoginType.LOCAL)
                .build();
    }

    private int insert(String name, String domain, String subdomain, Integer baseDomainSeq) {
        Tenant t = newTenant(name, domain, subdomain, baseDomainSeq);
        repository.insert(t);
        return t.getTenantSeq();
    }

    private static TenantListQuery page(Long cursorId, int pageSize) {
        return TenantListQuery.builder().page(CursorPaginationQuery.of(null, cursorId, null, pageSize)).build();
    }

    /** 변경 감지 결과는 메모리가 아니라 DB 값으로 확인 — flush(커밋 대신) 후 JdbcTemplate */
    private Map<String, Object> dbRow(int tenantSeq) {
        em.flush();
        return jdbc.queryForMap("SELECT tenant_name, domain, subdomain, base_domain_seq, login_type, register_user_seq, register_date, update_user_seq, update_date "
                + "FROM tb_tenant WHERE tenant_seq = ?", tenantSeq);
    }

    @Test
    @DisplayName("findByTenantSeq — 시드 FULL 매핑(등록자 조인 user_id/user_name·update 조인 NULL·enum 3종·Instant), 없는 seq 와 null seq 는 null")
    void findByTenantSeq_fullMapping() {
        TenantData t = queryRepository.findByTenantSeq(SEED_TENANT);

        assertThat(t).isNotNull();
        assertThat(t.getTenantSeq()).isEqualTo(SEED_TENANT);
        assertThat(t.getTenantName()).isEqualTo("기본 테넌트");
        assertThat(t.getDomain()).isNull();
        assertThat(t.getTenantType()).isEqualTo(TenantType.SELF_MANAGED);
        assertThat(t.getUserMode()).isEqualTo(TenantUserMode.MULTI);
        assertThat(t.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(t.getLoginType()).isEqualTo(LoginType.LOCAL);
        assertThat(t.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(t.getRegisterUserId()).isEqualTo("superadmin");
        assertThat(t.getRegisterUserName()).isEqualTo("슈퍼어드민");
        assertThat(t.getRegisterDate()).isNotNull();
        assertThat(t.getUpdateUserSeq()).isNull();
        assertThat(t.getUpdateUserId()).as("LEFT JOIN — 수정자 없음").isNull();
        assertThat(t.getUpdateDate()).isNull();

        assertThat(queryRepository.findByTenantSeq(999_999)).isNull();
        assertThat(queryRepository.findByTenantSeq(null)).isNull();
    }

    @Test
    @DisplayName("insert — identity PK 가 엔티티에 주입(시드보다 큼), register 는 컨텍스트 사용자·초 절삭, 이후 BASIC 조회 2종(도메인·서브도메인)에서 감사 필드는 null")
    void insert_assignsGeneratedKey_thenBasicLookups() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Tenant t = newTenant("acme", "acme.drive.test", "acme", 1);
        repository.insert(t);

        assertThat(t.getTenantSeq()).isNotNull().isGreaterThan(SEED_TENANT);

        TenantData full = queryRepository.findByTenantSeq(t.getTenantSeq());
        assertThat(full.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(full.getRegisterDate()).isAfterOrEqualTo(before);
        assertThat(full.getRegisterDate().getNano()).as("초 단위 절삭").isZero();

        TenantData byDomain = queryRepository.findByDomain("acme.drive.test");
        assertThat(byDomain.getTenantSeq()).isEqualTo(t.getTenantSeq());
        assertThat(byDomain.getTenantName()).isEqualTo("acme");
        assertThat(byDomain.getSubdomain()).isEqualTo("acme");
        assertThat(byDomain.getBaseDomainSeq()).isEqualTo(1);
        assertThat(byDomain.getTenantType()).isEqualTo(TenantType.SELF_MANAGED);
        assertThat(byDomain.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(byDomain.getLoginType()).isEqualTo(LoginType.LOCAL);
        assertThat(byDomain.getRegisterUserSeq()).as("BASIC 은 감사 컬럼을 내리지 않음").isNull();
        assertThat(byDomain.getRegisterDate()).isNull();

        assertThat(queryRepository.findBySubdomain("acme").getTenantSeq()).isEqualTo(t.getTenantSeq());

        assertThat(queryRepository.findByDomain("nope.drive.test")).isNull();
        assertThat(queryRepository.findByDomain(null)).as("domain NULL 시드는 = NULL 비교로 안 잡힘").isNull();
        assertThat(queryRepository.findBySubdomain("nope")).isNull();
    }

    @Test
    @DisplayName("findBySubdomain — 서브도메인 중복 시 tenant_seq 최솟값(가장 오래된 테넌트) 1건")
    void findBySubdomain_duplicatePicksOldest() {
        int first = insert("dup-a", "dup.a.test", "dup", null);
        insert("dup-b", "dup.b.test", "dup", null);

        assertThat(queryRepository.findBySubdomain("dup").getTenantSeq()).isEqualTo(first);
    }

    @Test
    @DisplayName("findAll — tenant_seq DESC, 커서 없으면 첫 페이지, cursorId 미만으로 다음 페이지, FULL 조인 필드 포함, LIMIT 준수")
    void findAll_cursorPaging() {
        int a = insert("p-a", "p-a.test", "p-a", null);
        int b = insert("p-b", "p-b.test", "p-b", null);
        int c = insert("p-c", "p-c.test", "p-c", null);

        List<TenantData> page1 = queryRepository.findAll(page(null, 2));
        assertThat(page1).extracting(TenantData::getTenantSeq).containsExactly(c, b);
        assertThat(page1.get(0).getRegisterUserId()).isEqualTo("superadmin");
        assertThat(page1.get(0).getLoginType()).as("findAll 도 FULL 형태(login_type 포함)").isEqualTo(LoginType.LOCAL);

        List<TenantData> page2 = queryRepository.findAll(page((long) b, 2));
        assertThat(page2).extracting(TenantData::getTenantSeq).containsExactly(a, SEED_TENANT);

        assertThat(queryRepository.findAll(page((long) SEED_TENANT, 2))).isEmpty();
        assertThat(queryRepository.findAll(page(null, 10))).hasSize(4);
    }

    @Test
    @DisplayName("existsByDomain — 있으면 true, 자기 seq 제외면 false, 다른 seq 제외면 true, 없는 도메인 false")
    void existsByDomain_withOptionalExclusion() {
        int seq = insert("ex", "ex.drive.test", "ex", null);

        assertThat(queryRepository.existsByDomain("ex.drive.test", null)).isTrue();
        assertThat(queryRepository.existsByDomain("ex.drive.test", seq)).as("수정 시 자기 자신 제외").isFalse();
        assertThat(queryRepository.existsByDomain("ex.drive.test", SEED_TENANT)).isTrue();
        assertThat(queryRepository.existsByDomain("none.drive.test", null)).isFalse();
    }

    @Test
    @DisplayName("SELF_MANAGED 조회 — Seqs 는 상태 무관 전부, ActiveSeqs 는 LIMIT 2, count 는 ACTIVE 전부, SYSTEM_MANAGED 는 모두 제외 / 쓰기 리포 재바인딩 대상 2종(DomainIsNotNull·BaseDomainSeq) 은 관리 엔티티")
    void selfManagedQueries() {
        int a = insert("org-a", "org-a.test", "org-a", null);
        int b = insert("org-b", "org-b.test", "org-b", 1);
        int inactive = insert("org-c", "org-c.test", "org-c", null);
        Tenant system = newTenant("sys-a", "sys-a.test", "sys-a", null, TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE);
        repository.insert(system);
        jdbc.update("UPDATE tb_tenant SET status = 'INACTIVE' WHERE tenant_seq = ?", inactive);
        em.clear(); // JdbcTemplate 로 바꾼 상태를 아래 엔티티 로드가 DB 에서 읽도록

        assertThat(queryRepository.findSelfManagedTenantSeqs()).containsExactlyInAnyOrder(SEED_TENANT, a, b, inactive);
        assertThat(queryRepository.findActiveSelfManagedTenantSeqs()).as("ACTIVE 3개지만 LIMIT 2").hasSize(2).doesNotContain(inactive);
        assertThat(queryRepository.countActiveSelfManagedTenants()).isEqualTo(3);
        TenantData systemData = queryRepository.findByTenantSeq(system.getTenantSeq());
        assertThat(systemData.getTenantType()).isEqualTo(TenantType.SYSTEM_MANAGED);
        assertThat(systemData.getUserMode()).isEqualTo(TenantUserMode.SINGLE);

        List<Tenant> withDomain = repository.findByTenantTypeAndDomainIsNotNull(TenantType.SELF_MANAGED);
        assertThat(withDomain).extracting(Tenant::getTenantSeq)
                .as("domain NULL 인 시드 1 제외, INACTIVE 포함, SYSTEM_MANAGED 제외")
                .containsExactlyInAnyOrder(a, b, inactive);
        assertThat(withDomain).allMatch(em::contains);

        assertThat(repository.findByBaseDomainSeq(1)).extracting(Tenant::getTenantSeq).containsExactly(b);
        assertThat(repository.findByBaseDomainSeq(999)).isEmpty();
    }

    @Test
    @DisplayName("변경 감지 — findById 관리 엔티티 + changeProfile/changeDomainBinding/changeLoginType 이 flush 때 DB 에 반영(update_* 현재 사용자, register 보존, 이름·login_type 보존), 없는 seq 는 null")
    void changeDetection() {
        int seq = insert("up", "up.old.test", "up", null);
        Map<String, Object> before = dbRow(seq);
        em.clear(); // 아래 findById 가 이 트랜잭션의 첫 로드가 되도록

        loginAs(ADMIN);
        Tenant t = repository.findById(seq);
        assertThat(em.contains(t)).as("쓰기 리포 findById = 관리 엔티티").isTrue();
        t.changeProfile("up2", "up.new.test", "up2", 1);
        Map<String, Object> u = dbRow(seq);
        assertThat(u.get("tenant_name")).isEqualTo("up2");
        assertThat(u.get("domain")).isEqualTo("up.new.test");
        assertThat(u.get("subdomain")).isEqualTo("up2");
        assertThat(u.get("base_domain_seq")).isEqualTo(1);
        assertThat(u.get("update_user_seq")).isEqualTo(ADMIN);
        assertThat(u.get("update_date")).isNotNull();
        assertThat(u.get("register_user_seq")).isEqualTo(SUPER_ADMIN);
        assertThat(u.get("register_date")).isEqualTo(before.get("register_date"));
        assertThat(u.get("login_type")).as("changeProfile 은 login_type 안 건드림").isEqualTo("LOCAL");

        t.changeDomainBinding("up.rebound.test", "up3", null);
        Map<String, Object> r = dbRow(seq);
        assertThat(r.get("tenant_name")).as("바인딩 갱신은 이름 보존").isEqualTo("up2");
        assertThat(r.get("domain")).isEqualTo("up.rebound.test");
        assertThat(r.get("subdomain")).isEqualTo("up3");
        assertThat(r.get("base_domain_seq")).isNull();

        t.changeLoginType(LoginType.OIDC);
        assertThat(dbRow(seq).get("login_type")).isEqualTo("OIDC");

        assertThat(repository.findById(999_999)).isNull();
    }

    @Test
    @DisplayName("감사 — 시큐리티 사용자 우선(선언 사용자 덮어씀, JPA 등록일은 현재 시각), 수정은 Auditing, 컨텍스트 없으면 INSERT 는 NOT NULL 위반")
    void audit_securityUserWins_andNoContextFails() {
        Instant fixed = Instant.parse("2026-01-02T03:04:05Z");
        Tenant t = Tenant.builder().tenantName("au").domain("au.test").subdomain("au")
                .tenantType(TenantType.SELF_MANAGED).userMode(TenantUserMode.MULTI).status(TenantStatus.ACTIVE).loginType(LoginType.LOCAL)
                .registerUserSeq(ADMIN).registerDate(fixed).build();
        repository.insert(t);
        TenantData d = queryRepository.findByTenantSeq(t.getTenantSeq());
        assertThat(d.getRegisterUserSeq()).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(SUPER_ADMIN);
        assertThat(d.getRegisterDate()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixed);

        repository.findById(t.getTenantSeq()).changeLoginType(LoginType.JWT);
        TenantData u = queryRepository.findByTenantSeq(t.getTenantSeq()); // JPQL 직전 자동 flush
        assertThat(u.getLoginType()).isEqualTo(LoginType.JWT);
        assertThat(u.getUpdateUserSeq()).as("변경 감지 — 시큐리티 사용자(Auditing)").isEqualTo(SUPER_ADMIN);
        assertThat(u.getUpdateDate()).as("변경 감지 — 수정 시각은 Auditing").isNotNull();

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> repository.insert(newTenant("no-ctx", "no-ctx.test", "no-ctx", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제약 전파 — 도메인 유니크 위반·대문자 도메인 CHECK 위반은 DataIntegrityViolationException")
    void constraints_propagate() {
        insert("c1", "same.drive.test", "c1", null);
        assertThatThrownBy(() -> insert("c2", "same.drive.test", "c2", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제약 전파 — 대문자 도메인은 chk_tenant_domain_lowercase 위반")
    void constraints_uppercaseDomainRejected() {
        assertThatThrownBy(() -> insert("c3", "Upper.Drive.test", "c3", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
