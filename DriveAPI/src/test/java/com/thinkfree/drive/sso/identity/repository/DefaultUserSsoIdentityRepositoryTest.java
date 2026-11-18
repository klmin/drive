package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.entity.UserSsoIdentity;
import com.thinkfree.drive.sso.identity.query.UserSsoIdentityFindQuery;
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
 * 2단계 UserSsoIdentity 이관 회귀 — §1 v2 "JPA 우선", 구 XML 4 statement 전부 JPA(JdbcClient 0). 읽기({@link DefaultUserSsoIdentityQueryRepository}: **JPQL 생성자 식 첫 적용** — User 조인 DTO)·쓰기({@link DefaultUserSsoIdentityRepository}: saveAndFlush·JPQL update·IN 삭제).
 * 시드 = user 1 superadmin·2 admin·3 anonymous(tenant 1). sso_provider_seq 는 FK 없음(임의 값). 트랜잭션 롤백.
 * 검증 포인트 = identity 키·linked_at 리스너(엔티티 값 우선)·last_callback_at null, findWithUser 가 TB_USER 의 tenant·status·mfa·signupType 을 그대로(INNER JOIN — 사용자 없는 identity 는 null), (provider, tenant, subject) 불일치 null, 같은 IdP 계정의 테넌트별 연결,
 * updateLastCallbackAt(Instant µs)·없는 seq no-op, IN 삭제(빈·널 0·다른 사용자 무영향), (provider, subject) 유니크 위반은 맨 끝.
 */
@DisplayName("DefaultUserSsoIdentityRepository / DefaultUserSsoIdentityQueryRepository — JPA + JPQL 생성자 식(PostgreSQL Testcontainers)")
class DefaultUserSsoIdentityRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;
    private static final int TENANT = 1;
    private static final int OTHER_TENANT = 2;
    private static final int PROVIDER_A = 910001;
    private static final int PROVIDER_B = 910002;

    @Autowired
    private UserSsoIdentityRepository repository;
    @Autowired
    private UserSsoIdentityQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static UserSsoIdentity identity(int userSeq, int providerSeq, String subject) {
        return identity(userSeq, TENANT, providerSeq, subject);
    }

    private static UserSsoIdentity identity(int userSeq, int tenantSeq, int providerSeq, String subject) {
        return UserSsoIdentity.builder().userSeq(userSeq).tenantSeq(tenantSeq).ssoProviderSeq(providerSeq).subject(subject).email(subject + "@idp.example.com").name("name-" + subject).build();
    }

    private UserSsoIdentityData find(int providerSeq, String subject) {
        return find(providerSeq, TENANT, subject);
    }

    private UserSsoIdentityData find(int providerSeq, int tenantSeq, String subject) {
        return queryRepository.findWithUser(UserSsoIdentityFindQuery.builder().ssoProviderSeq(providerSeq).tenantSeq(tenantSeq).subject(subject).build());
    }

    private Map<String, Object> row(long seq) {
        return jdbc.queryForMap("SELECT user_seq, tenant_seq, sso_provider_seq, subject, email, name, linked_at, last_callback_at FROM tb_user_sso_identity WHERE sso_identity_seq = ?", seq);
    }

    @Test
    @DisplayName("save(W4 — saveAndFlush 아님, IDENTITY 라 호출 순간 INSERT: JdbcTemplate 로 바로 보임 · identity·linked_at 리스너·엔티티 값 우선·last_callback_at null) → findWithUser(JPQL 생성자 식: TB_USER 조인 6필드, 불일치 null, 사용자 없는 identity null) / (provider, subject) 유니크 위반은 맨 끝")
    void saveAndFindWithUser() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UserSsoIdentity a = identity(ADMIN, PROVIDER_A, "sub-admin");
        repository.save(a);
        assertThat(a.getSsoIdentitySeq()).as("identity 키(bigint → Long) 주입").isNotNull().isPositive();
        assertThat(a.getLinkedAt()).as("리스너").isNotNull().isAfterOrEqualTo(before);
        assertThat(a.getLastCallbackAt()).isNull();
        Map<String, Object> dbA = row(a.getSsoIdentitySeq());
        assertThat(dbA.get("user_seq")).isEqualTo(ADMIN);
        assertThat(dbA.get("tenant_seq")).isEqualTo(TENANT);
        assertThat(dbA.get("subject")).isEqualTo("sub-admin");
        assertThat(dbA.get("email")).isEqualTo("sub-admin@idp.example.com");
        assertThat(dbA.get("linked_at")).isNotNull();
        assertThat(dbA.get("last_callback_at")).as("구 INSERT 가 넣지 않던 컬럼 → NULL").isNull();

        Instant fixedLinked = Instant.parse("2025-12-31T23:59:59Z");
        UserSsoIdentity b = UserSsoIdentity.builder().userSeq(ANONYMOUS).tenantSeq(TENANT).ssoProviderSeq(PROVIDER_A).subject("sub-anon").linkedAt(fixedLinked).build();
        repository.save(b);
        assertThat(b.getLinkedAt()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixedLinked);
        assertThat(row(b.getSsoIdentitySeq()).get("email")).isNull();

        // findWithUser — TB_USER 의 값과 동일
        Map<String, Object> user = jdbc.queryForMap("SELECT tenant_seq, status, mfa_status, signup_type FROM tb_user WHERE user_seq = ?", ADMIN);
        UserSsoIdentityData found = find(PROVIDER_A, "sub-admin");
        assertThat(found).isNotNull();
        assertThat(found.getSsoIdentitySeq()).isEqualTo(a.getSsoIdentitySeq());
        assertThat(found.getUserSeq()).isEqualTo(ADMIN);
        assertThat(found.getTenantSeq()).isEqualTo(user.get("tenant_seq"));
        assertThat(found.getUserStatus().name()).isEqualTo(user.get("status"));
        assertThat(found.getMfaStatus().name()).isEqualTo(user.get("mfa_status"));
        assertThat(found.getSignupType().name()).isEqualTo(user.get("signup_type"));
        assertThat(find(PROVIDER_A, "sub-anon").getUserSeq()).isEqualTo(ANONYMOUS);
        assertThat(find(PROVIDER_B, "sub-admin")).as("provider 불일치").isNull();
        assertThat(find(PROVIDER_A, "no-such")).as("subject 불일치").isNull();
        assertThat(find(PROVIDER_A, OTHER_TENANT, "sub-admin")).as("테넌트 불일치").isNull();

        // 사용자 행이 없는 identity 는 INNER JOIN 에서 빠짐
        repository.save(identity(999999, PROVIDER_B, "sub-ghost"));
        assertThat(find(PROVIDER_B, "sub-ghost")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_sso_identity WHERE subject = 'sub-ghost'", Integer.class)).isEqualTo(1);

        // uniq_provider_subject_tenant 위반은 PG 트랜잭션 abort → 맨 끝
        assertThatThrownBy(() -> repository.save(identity(ANONYMOUS, PROVIDER_A, "sub-admin"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("updateLastCallbackAt(JPQL, Instant µs 왕복·없는 seq no-op) / deleteByUserSeqList(IN·빈·널 0·다른 사용자 무영향)")
    void updateAndDelete() {
        UserSsoIdentity a = identity(ADMIN, PROVIDER_A, "sub-admin");
        UserSsoIdentity a2 = identity(ADMIN, PROVIDER_B, "sub-admin-b");
        UserSsoIdentity c = identity(ANONYMOUS, PROVIDER_A, "sub-anon");
        repository.save(a);
        repository.save(a2);
        repository.save(c);

        Instant at = Instant.parse("2026-03-03T03:03:03.123456Z");
        repository.updateLastCallbackAt(a.getSsoIdentitySeq(), at);
        OffsetDateTime db = jdbc.queryForObject("SELECT last_callback_at FROM tb_user_sso_identity WHERE sso_identity_seq = ?", OffsetDateTime.class, a.getSsoIdentitySeq());
        assertThat(db.toInstant()).isCloseTo(at, within(1, ChronoUnit.MICROS));
        assertThat(row(a2.getSsoIdentitySeq()).get("last_callback_at")).as("다른 행 무영향").isNull();
        repository.updateLastCallbackAt(999999L, at);

        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(ADMIN, 999999))).isEqualTo(2);
        assertThat(find(PROVIDER_A, "sub-admin")).isNull();
        assertThat(find(PROVIDER_B, "sub-admin-b")).isNull();
        assertThat(find(PROVIDER_A, "sub-anon")).as("다른 사용자 무영향").isNotNull();
        assertThat(repository.deleteByUserSeqList(List.of(ANONYMOUS))).isEqualTo(1);
        assertThat(find(PROVIDER_A, "sub-anon")).isNull();
    }

    @Test
    @DisplayName("GLOBAL Provider 의 같은 IdP 계정(provider, subject)을 다른 테넌트 계정에 연결 가능 · 테넌트별로 조회 / 같은 테넌트 두 번째 연결은 유니크 위반(맨 끝)")
    void sameSubjectPerTenant() {
        repository.save(identity(ADMIN, TENANT, PROVIDER_A, "sub-shared"));
        repository.save(identity(ANONYMOUS, OTHER_TENANT, PROVIDER_A, "sub-shared"));

        assertThat(find(PROVIDER_A, TENANT, "sub-shared").getUserSeq()).isEqualTo(ADMIN);
        assertThat(find(PROVIDER_A, OTHER_TENANT, "sub-shared").getUserSeq()).isEqualTo(ANONYMOUS);

        // 같은 테넌트의 다른 사용자(uniq_user_provider 와 겹치지 않게 새 사용자)에 같은 IdP 계정 → uniq_provider_subject_tenant 위반
        Integer another = jdbc.queryForObject("INSERT INTO tb_user(user_id, user_name, tenant_seq, register_user_seq) VALUES ('sso-another@test.com', 'another', ?, 1) RETURNING user_seq",
                Integer.class, TENANT);
        assertThatThrownBy(() -> repository.save(identity(another, TENANT, PROVIDER_A, "sub-shared"))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
