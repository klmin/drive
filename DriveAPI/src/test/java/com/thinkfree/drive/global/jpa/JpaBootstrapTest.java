package com.thinkfree.drive.global.jpa;

import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.repository.ApiKeyQueryRepository;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.repository.TenantQueryRepository;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * JPA 부트스트랩 검증(핸드오프 ★★★ §1-1). 시드 = tenant 1, user 1(superadmin)·2(admin)·3(anonymous). 트랜잭션 롤백.
 * <ol>
 *   <li>컨텍스트 기동 — JPA·JdbcClient 빈 공존, MyBatis 빈 없음(10-06 제거), 트랜잭션 매니저 = {@link JpaTransactionManager} 하나</li>
 *   <li>JPA persist 후 같은 트랜잭션에서 JdbcClient 리포({@link TenantQueryRepository}) 조회에 보임 — flush 확증</li>
 *   <li>JPA persist 후 JdbcClient 조회에 보임(같은 커넥션) · 다른 트랜잭션(REQUIRES_NEW)에서는 안 보임(커밋 전)</li>
 *   <li>테스트 롤백이 JPA 쓰기도 되돌림</li>
 *   <li>Spring Data JPA Auditing — 시큐리티 사용자 우선(선언 값 덮어씀) · 없으면 선언 값 · 둘 다 없으면 NOT NULL 위반 · 초 절삭 · UPDATE</li>
 *   <li>Y/N({@code YesNoConverter})·Instant(Hibernate 기본 매핑) 왕복 — JdbcClient 경로와 같은 절대 시각</li>
 * </ol>
 */
@DisplayName("JPA 부트스트랩 — Hibernate + JdbcClient 공존(PostgreSQL Testcontainers)")
class JpaBootstrapTest extends RepositoryTest {

    private static final int SEED_TENANT = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final Instant FIXED = Instant.parse("2025-01-02T03:04:05Z");
    private static final Instant FUTURE = Instant.parse("2030-01-02T03:04:05.123456Z");

    @Autowired
    private ApplicationContext context;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TenantQueryRepository tenantQueryRepository;
    @Autowired
    private ApiKeyQueryRepository apiKeyQueryRepository;
    @Autowired
    private JdbcClient jdbcClient;

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

    private static Tenant newTenant(String name) {
        return Tenant.builder()
                .tenantName(name).subdomain(name)
                .tenantType(TenantType.SELF_MANAGED).userMode(TenantUserMode.MULTI).status(TenantStatus.ACTIVE).loginType(LoginType.LOCAL)
                .build();
    }

    private Tenant persistTenant(String name) {
        Tenant tenant = newTenant(name);
        em.persist(tenant);
        em.flush();
        assertThat(tenant.getTenantSeq()).as("identity PK 주입").isNotNull().isGreaterThan(SEED_TENANT);
        return tenant;
    }

    private int countTenantByJdbc(int tenantSeq) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tb_tenant WHERE tenant_seq = ?", Integer.class, tenantSeq);
        return count == null ? 0 : count;
    }

    private TenantData findTenant(int tenantSeq) {
        return tenantQueryRepository.findByTenantSeq(tenantSeq);
    }

    @Nested
    @DisplayName("① 컨텍스트 기동")
    class ContextBoot {

        @Test
        @DisplayName("JPA·JdbcClient 빈이 함께 뜨고 MyBatis 빈은 없으며 트랜잭션 매니저는 JpaTransactionManager 하나")
        void beansCoexist() {
            assertThat(context.getBean(EntityManagerFactory.class)).isNotNull();
            assertThat(context.getBean(JdbcClient.class)).isNotNull();
            assertThat(context.containsBean("sqlSessionFactory")).as("MyBatis 제거(10-06)").isFalse();
            assertThat(context.containsBean("sqlSessionTemplate")).as("MyBatis 제거(10-06)").isFalse();
            assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
            assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        }
    }

    @Nested
    @DisplayName("② ③ ④ 같은 트랜잭션 공유")
    class SharedTransaction {

        @Test
        @DisplayName("② JPA persist 직후 JdbcClient 리포 조회에 보인다(enum·Instant 왕복 포함)")
        void jpaWriteVisibleToJdbcClient() {
            Tenant tenant = Tenant.builder()
                    .tenantName("jpa-visible").subdomain("jpa-visible")
                    .tenantType(TenantType.SELF_MANAGED).userMode(TenantUserMode.MULTI).status(TenantStatus.INACTIVE).loginType(LoginType.LOCAL)
                    .registerUserSeq(ADMIN).registerDate(FIXED)
                    .build();
            em.persist(tenant);
            em.flush();

            TenantData data = findTenant(tenant.getTenantSeq());
            assertThat(data).isNotNull();
            assertThat(data.getTenantName()).isEqualTo("jpa-visible");
            assertThat(data.getStatus()).isEqualTo(TenantStatus.INACTIVE);
            assertThat(data.getTenantType()).isEqualTo(TenantType.SELF_MANAGED);
            assertThat(data.getRegisterUserSeq()).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(SUPER_ADMIN);
            assertThat(data.getRegisterDate()).as("Instant(Hibernate 기본 TIMESTAMP_UTC) ↔ timestamptz 왕복").isEqualTo(tenant.getRegisterDate());
        }

        @Test
        @DisplayName("③ JPA persist 가 같은 트랜잭션의 JdbcClient 조회에는 보이고, 다른 트랜잭션(REQUIRES_NEW)에는 커밋 전이라 안 보인다")
        void jpaWriteVisibleOnSameConnectionOnly() {
            Tenant tenant = persistTenant("jpa-same-tx");
            int seq = tenant.getTenantSeq();

            assertThat(jdbcClient.sql("SELECT COUNT(*) FROM tb_tenant WHERE tenant_seq = :seq").param("seq", seq).query(Integer.class).single())
                    .as("같은 트랜잭션의 JdbcClient 조회").isEqualTo(1);
            assertThat(countTenantByJdbc(seq)).as("같은 트랜잭션의 JdbcTemplate 조회").isEqualTo(1);

            TransactionTemplate other = new TransactionTemplate(transactionManager);
            other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer fromOtherTx = other.execute(status -> countTenantByJdbc(seq));
            assertThat(fromOtherTx).as("별도 커넥션에서는 미커밋 행이 안 보여야 같은 트랜잭션 공유가 증명된다").isEqualTo(0);
        }

        @Test
        @DisplayName("④ 테스트 트랜잭션 롤백이 JPA 쓰기도 되돌린다")
        void rollbackRevertsJpaWrite() {
            Tenant tenant = persistTenant("jpa-rollback");
            int seq = tenant.getTenantSeq();
            assertThat(countTenantByJdbc(seq)).isEqualTo(1);

            TestTransaction.flagForRollback();
            TestTransaction.end();
            try {
                assertThat(countTenantByJdbc(seq)).as("롤백 후 autocommit 조회").isEqualTo(0);
            } finally {
                TestTransaction.start();
            }
        }
    }

    @Nested
    @DisplayName("⑤ Spring Data JPA Auditing(AuditConfig) — 시큐리티 사용자 우선, 없으면 선언 값")
    class Auditing {

        @Test
        @DisplayName("persist — register_* 를 시큐리티 사용자·현재 시각(초 절삭)으로 채우고 update_* 는 비워 둔다(modifyOnCreate=false)")
        void persistFillsRegisterFields() {
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Tenant tenant = persistTenant("audit-default");

            assertThat(tenant.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(tenant.getRegisterDate()).isNotNull();
            assertThat(tenant.getRegisterDate().getNano()).as("초 절삭").isZero();
            assertThat(tenant.getRegisterDate()).isBetween(before, Instant.now().plusSeconds(1));
            assertThat(tenant.getUpdateUserSeq()).isNull();
            assertThat(tenant.getUpdateDate()).isNull();

            TenantData data = findTenant(tenant.getTenantSeq());
            assertThat(data.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(data.getRegisterDate()).isEqualTo(tenant.getRegisterDate());
            assertThat(data.getUpdateUserSeq()).isNull();
            assertThat(data.getUpdateDate()).isNull();
        }

        @Test
        @DisplayName("시큐리티 사용자가 있으면 엔티티에 넣은 등록자·등록일을 덮어쓴다")
        void persistOverwritesDeclaredValuesWhenAuthenticated() {
            Tenant tenant = Tenant.builder()
                    .tenantName("audit-explicit").subdomain("audit-explicit")
                    .tenantType(TenantType.SELF_MANAGED).userMode(TenantUserMode.MULTI).status(TenantStatus.ACTIVE).loginType(LoginType.LOCAL)
                    .registerUserSeq(ADMIN).registerDate(FIXED)
                    .build();
            em.persist(tenant);
            em.flush();

            assertThat(tenant.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(tenant.getRegisterDate()).isNotEqualTo(FIXED);
            assertThat(findTenant(tenant.getTenantSeq()).getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
        }

        @Test
        @DisplayName("시큐리티 사용자가 없으면(스케줄러 등) 선언한 등록자를 그대로 둔다")
        void persistKeepsDeclaredUserWithoutSecurityContext() {
            SecurityContextHolder.clearContext();
            Tenant tenant = Tenant.builder()
                    .tenantName("audit-system").subdomain("audit-system")
                    .tenantType(TenantType.SELF_MANAGED).userMode(TenantUserMode.MULTI).status(TenantStatus.ACTIVE).loginType(LoginType.LOCAL)
                    .registerUserSeq(ADMIN)
                    .build();
            em.persist(tenant);
            em.flush();

            assertThat(findTenant(tenant.getTenantSeq()).getRegisterUserSeq()).isEqualTo(ADMIN);
            assertThat(tenant.getRegisterDate()).isNotNull();
        }

        @Test
        @DisplayName("시큐리티 사용자도 선언 값도 없으면 register_user_seq 가 null 이라 NOT NULL 위반으로 실패한다")
        void persistWithoutSecurityContextAndDeclaredUserFailsOnNotNull() {
            SecurityContextHolder.clearContext();
            Tenant tenant = newTenant("audit-no-context");

            assertThatThrownBy(() -> {
                em.persist(tenant);
                em.flush();
            }).isInstanceOf(PersistenceException.class)
              .hasMessageContaining("register_user_seq");
        }

        @Test
        @DisplayName("dirty checking UPDATE — update_* 를 시큐리티 사용자·현재 시각으로 채우고, 엔티티에 넣은 값도 덮어쓴다")
        void updateFillsUpdateFields() {
            Tenant tenant = persistTenant("audit-update");
            int seq = tenant.getTenantSeq();

            loginAs(ADMIN);
            ReflectionTestUtils.setField(tenant, "tenantName", "audit-update-renamed");
            em.flush();

            assertThat(tenant.getUpdateUserSeq()).isEqualTo(ADMIN);
            assertThat(tenant.getUpdateDate()).isNotNull();
            assertThat(tenant.getUpdateDate().getNano()).isZero();
            TenantData data = findTenant(seq);
            assertThat(data.getTenantName()).isEqualTo("audit-update-renamed");
            assertThat(data.getUpdateUserSeq()).isEqualTo(ADMIN);
            assertThat(data.getUpdateDate()).isEqualTo(tenant.getUpdateDate());
            assertThat(data.getRegisterUserSeq()).as("register_* 보존").isEqualTo(SUPER_ADMIN);

            ReflectionTestUtils.setField(tenant, "updateUserSeq", SUPER_ADMIN);
            ReflectionTestUtils.setField(tenant, "updateDate", FIXED);
            ReflectionTestUtils.setField(tenant, "tenantName", "audit-update-explicit");
            em.flush();

            TenantData overwritten = findTenant(seq);
            assertThat(overwritten.getUpdateUserSeq()).as("선언 값보다 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(overwritten.getUpdateDate()).isNotEqualTo(FIXED);
        }
    }

    @Nested
    @DisplayName("⑥ 컨버터 왕복")
    class Converters {

        @Test
        @DisplayName("Instant ↔ timestamptz: JPA 재조회·JdbcClient 리포·raw OffsetDateTime 이 같은 절대 시각(마이크로초 포함)")
        void instantRoundTrip() {
            ApiKey apiKey = ApiKey.builder()
                    .tenantSeq(SEED_TENANT).userSeq(ADMIN)
                    .keyName("bootstrap").keyPurpose("bootstrap").apiKey("bootstrap-" + System.nanoTime())
                    .roleType(RoleType.USER).scope("api:read").status(ApiKeyStatus.ACTIVE)
                    .expireDate(FUTURE)
                    .build();
            em.persist(apiKey);
            em.flush();
            em.clear();

            ApiKey reloaded = em.find(ApiKey.class, apiKey.getApiKeySeq());
            assertThat(reloaded.getExpireDate()).isEqualTo(FUTURE);
            assertThat(reloaded.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);

            Instant raw = jdbc.queryForObject("SELECT expire_date FROM tb_api_key WHERE api_key_seq = ?",
                    (rs, i) -> rs.getObject("expire_date", OffsetDateTime.class).toInstant(), apiKey.getApiKeySeq());
            assertThat(raw).as("JVM·세션 시간대와 무관한 절대 시각").isEqualTo(FUTURE);

            var data = apiKeyQueryRepository.findById(apiKey.getApiKeySeq());
            assertThat(data).isNotNull();
            assertThat(data.getExpireDate()).as("Instant 왕복 값 일치").isEqualTo(FUTURE);
        }

        @Test
        @DisplayName("Y/N Boolean ↔ char(1): 'Y'/'N' 로 저장되고 Boolean 으로 되돌아온다")
        void yesNoRoundTrip() {
            Tenant owner = persistTenant("yesno-owner"); // tb_share_setting 은 테넌트당 1행(uk_scope) — 시드 tenant 1 과 안 겹치게
            YesNoProbeEntity probe = YesNoProbeEntity.builder()
                    .tenantSeq(owner.getTenantSeq())
                    .isEnforced(false).isExternalShareAllowed(true).isSpecificUsersAllowed(false)
                    .isOrgLinkAllowed(true).isPublicAllowed(false)
                    .build();
            em.persist(probe);
            em.flush();
            em.clear();

            List<String> raw = jdbc.queryForList(
                    "SELECT is_enforced || is_external_share_allowed || is_specific_users_allowed || is_org_link_allowed || is_public_allowed"
                            + " FROM tb_share_setting WHERE share_setting_seq = ?", String.class, probe.getShareSettingSeq());
            assertThat(raw).containsExactly("NYNYN");

            YesNoProbeEntity reloaded = em.find(YesNoProbeEntity.class, probe.getShareSettingSeq());
            assertThat(reloaded.getIsEnforced()).isFalse();
            assertThat(reloaded.getIsExternalShareAllowed()).isTrue();
            assertThat(reloaded.getIsSpecificUsersAllowed()).isFalse();
            assertThat(reloaded.getIsOrgLinkAllowed()).isTrue();
            assertThat(reloaded.getIsPublicAllowed()).isFalse();
            assertThat(reloaded.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
        }
    }
}
