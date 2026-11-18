package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 SuperAdminLoginSetting 이관(JdbcClient) 회귀. 시드 없음(빈 테이블), 트랜잭션 롤백.
 * 검증 포인트 = 싱글턴 PK(1) 에 대한 upsert/upsertEnforce 의 "갱신 컬럼만 바뀌고 나머지 보존"(enforce 만 갱신 시 login_type 보존),
 * enum name ↔ varchar·Y/N ↔ boolean·Instant 왕복(record 매핑), 신규 행의 login_type NULL, 반환 건수, 감사 컬럼(JdbcAudit).
 */
@DisplayName("DefaultSuperAdminLoginSettingRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultSuperAdminLoginSettingRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private SuperAdminLoginSettingRepository repository;
    @Autowired
    private SuperAdminLoginSettingQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** converter.toUpsertEntity 형태 — loginType + isEnforced, 감사 필드 없음. */
    private static SuperAdminLoginSetting setting(LoginType loginType, Boolean enforced) {
        return SuperAdminLoginSetting.builder().loginType(loginType).isEnforced(enforced).build();
    }

    /** converter.toEnforceUpsertEntity 형태 — isEnforced 만. */
    private static SuperAdminLoginSetting enforce(boolean enforced) {
        return SuperAdminLoginSetting.builder().isEnforced(enforced).build();
    }

    private int rows() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tb_super_admin_login_setting", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("시드 행 — initialize.sql 이 LOCAL·개별적용(N) 1행을 넣는다, 지우면 find 는 null(예외 없음)")
    void find_seedRow_thenEmptyTable() {
        SuperAdminLoginSettingData seed = queryRepository.find();
        assertThat(seed).isNotNull();
        assertThat(seed.superAdminLoginSettingSeq()).isEqualTo(SuperAdminLoginSetting.SINGLETON_SEQ);
        assertThat(seed.loginType()).isEqualTo(LoginType.LOCAL);
        assertThat(seed.isEnforced()).isFalse();

        jdbc.update("DELETE FROM tb_super_admin_login_setting");
        assertThat(rows()).isZero();
        assertThat(queryRepository.find()).isNull();
    }

    @Test
    @DisplayName("upsert 2회 — 행 1개(seq 1), login_type·is_enforced 갱신, register 보존, update 는 두 번째 사용자, 반환 건수 1")
    void upsert_twice_singletonRowUpdated() {
        jdbc.update("DELETE FROM tb_super_admin_login_setting"); // 시드 행을 지우고 INSERT 경로부터
        loginAs(SUPER_ADMIN);
        assertThat(repository.upsert(setting(LoginType.OIDC, false))).isEqualTo(1);

        SuperAdminLoginSettingData first = queryRepository.find();
        assertThat(first).isNotNull();
        assertThat(first.superAdminLoginSettingSeq()).isEqualTo(SuperAdminLoginSetting.SINGLETON_SEQ);
        assertThat(first.loginType()).isEqualTo(LoginType.OIDC);
        assertThat(first.isEnforced()).isFalse();
        assertThat(first.registerUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(first.registerDate()).isNotNull();
        assertThat(first.updateUserSeq()).as("INSERT 는 register_* 만").isNull();
        assertThat(first.updateDate()).isNull();

        loginAs(ADMIN);
        assertThat(repository.upsert(setting(LoginType.JWT, true))).isEqualTo(1);

        assertThat(rows()).isEqualTo(1);
        SuperAdminLoginSettingData second = queryRepository.find();
        assertThat(second.loginType()).isEqualTo(LoginType.JWT);
        assertThat(second.isEnforced()).isTrue();
        assertThat(second.registerUserSeq()).as("register 보존").isEqualTo(SUPER_ADMIN);
        assertThat(second.registerDate()).isEqualTo(first.registerDate());
        assertThat(second.updateUserSeq()).isEqualTo(ADMIN);
        assertThat(second.updateDate()).isNotNull();
    }

    @Test
    @DisplayName("upsertEnforce — 기존 행의 is_enforced 만 바꾸고 login_type 보존; 신규 행이면 login_type NULL 로 생성")
    void upsertEnforce_preservesLoginType() {
        loginAs(SUPER_ADMIN);
        repository.upsert(setting(LoginType.OIDC, true));
        assertThat(repository.upsertEnforce(enforce(false))).isEqualTo(1);

        SuperAdminLoginSettingData d = queryRepository.find();
        assertThat(d.isEnforced()).isFalse();
        assertThat(d.loginType()).as("enforce 만 갱신 — login_type 보존").isEqualTo(LoginType.OIDC);
        assertThat(rows()).isEqualTo(1);

        jdbc.update("DELETE FROM tb_super_admin_login_setting");
        repository.upsertEnforce(enforce(true));
        SuperAdminLoginSettingData fresh = queryRepository.find();
        assertThat(fresh.isEnforced()).isTrue();
        assertThat(fresh.loginType()).as("신규 행은 login_type 없이 생성(NULL → record null)").isNull();
    }

    @Test
    @DisplayName("upsert 에 loginType null — login_type NULL 로 저장(nullable varchar, Types.VARCHAR 바인딩)")
    void upsert_nullLoginType() {
        loginAs(SUPER_ADMIN);
        repository.upsert(setting(LoginType.LOCAL, false));
        repository.upsert(setting(null, true));

        SuperAdminLoginSettingData d = queryRepository.find();
        assertThat(d.loginType()).isNull();
        assertThat(d.isEnforced()).isTrue();
    }

    @Test
    @DisplayName("감사 컬럼 — 엔티티 값 우선(그대로 저장), 없으면 SecurityContext 사용자 + 현재 시각(초 절삭), 컨텍스트 없으면 NOT NULL 위반")
    void audit_rules() {
        jdbc.update("DELETE FROM tb_super_admin_login_setting"); // 시드 행을 지우고 INSERT 경로부터
        Instant fixed = Instant.parse("2026-01-02T03:04:05.123456Z");
        repository.upsert(SuperAdminLoginSetting.builder().loginType(LoginType.LOCAL).isEnforced(false)
                .registerUserSeq(ADMIN).registerDate(fixed).build());
        SuperAdminLoginSettingData d = queryRepository.find();
        assertThat(d.registerUserSeq()).isEqualTo(ADMIN);
        assertThat(d.registerDate()).isEqualTo(fixed);

        loginAs(SUPER_ADMIN);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.upsertEnforce(enforce(true));
        SuperAdminLoginSettingData u = queryRepository.find();
        assertThat(u.updateUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(u.updateDate()).isAfterOrEqualTo(before);
        assertThat(u.updateDate().getNano()).as("초 단위 절삭").isZero();

        jdbc.update("DELETE FROM tb_super_admin_login_setting");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> repository.upsert(setting(LoginType.LOCAL, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("isEnforced null — NOT NULL 컬럼이라 DB 가 거부(MyBatis 때와 동일)")
    void upsertEnforce_nullFlagRejected() {
        loginAs(SUPER_ADMIN);
        assertThatThrownBy(() -> repository.upsertEnforce(SuperAdminLoginSetting.builder().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
