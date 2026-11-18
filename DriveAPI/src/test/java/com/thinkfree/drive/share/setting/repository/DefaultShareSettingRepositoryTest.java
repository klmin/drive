package com.thinkfree.drive.share.setting.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.share.setting.data.ShareSettingData;
import com.thinkfree.drive.share.setting.entity.ShareSetting;
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
 * 2단계 ShareSetting 이관(upsert JdbcClient·insert JPA persist) 회귀. 시드 = tenant 1 행(N,N,Y,Y,N, 등록자 1), 글로벌 행 없음. 트랜잭션 롤백.
 * 검증 포인트 = 표현식 유니크 ((COALESCE(tenant_seq,0))) 에 대한 5종 upsert 의 "갱신 컬럼만 바뀌고 나머지는 보존",
 * 감사 컬럼(SecurityContext 사용자 우선 → 엔티티 선언 값 → 없으면 NOT NULL 위반, 신규 행은 update_* NULL), Y/N 왕복, record 매핑.
 */
@DisplayName("DefaultShareSettingRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultShareSettingRepositoryTest extends RepositoryTest {

    private static final int TENANT = 1;
    private static final int NEW_TENANT = 77;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private ShareSettingRepository repository;
    @Autowired
    private ShareSettingQueryRepository queryRepository;
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

    /** 감사 필드 없는 엔티티(서비스가 넘기는 형태 — 인터셉터가 채우던 것). */
    private static ShareSetting flags(Integer tenantSeq, boolean enforced, boolean external, boolean specific, boolean orgLink, boolean pub) {
        return ShareSetting.builder()
                .tenantSeq(tenantSeq).isEnforced(enforced).isExternalShareAllowed(external)
                .isSpecificUsersAllowed(specific).isOrgLinkAllowed(orgLink).isPublicAllowed(pub)
                .build();
    }

    private static String yn(ShareSettingData d) {
        return d.isEnforced() + d.isExternalShareAllowed() + d.isSpecificUsersAllowed() + d.isOrgLinkAllowed() + d.isPublicAllowed();
    }

    private int count(String where) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tb_share_setting WHERE " + where, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("findByTenant — 시드 행 record 매핑(Y/N 원문·boolean 헬퍼·Instant), 없는 테넌트는 null, 글로벌은 시드 행(개별적용·테넌트 기본값과 같은 플래그)")
    void find_mapsSeedRow() {
        ShareSettingData d = queryRepository.findByTenant(TENANT);

        assertThat(d).isNotNull();
        assertThat(d.shareSettingSeq()).isNotNull();
        assertThat(d.tenantSeq()).isEqualTo(TENANT);
        assertThat(d.isGlobal()).isFalse();
        assertThat(yn(d)).isEqualTo("NNYYN");
        assertThat(d.enforced()).isFalse();
        assertThat(d.specificUsersAllowed()).isTrue();
        assertThat(d.orgLinkAllowed()).isTrue();
        assertThat(d.externalShareAllowed()).isFalse();
        assertThat(d.publicAllowed()).isFalse();
        assertThat(d.registerUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(d.registerDate()).isNotNull().isBefore(Instant.now().plusSeconds(60));
        assertThat(d.updateDate()).as("시드 행은 등록 후 미수정 → update_* NULL").isNull();
        assertThat(d.updateUserSeq()).isNull();

        assertThat(queryRepository.findByTenant(NEW_TENANT)).isNull();
        ShareSettingData global = queryRepository.findGlobal();
        assertThat(global).as("initialize.sql 글로벌 시드 — 없으면 슈퍼어드민 공유 설정 화면이 그려지지 않음").isNotNull();
        assertThat(global.isGlobal()).isTrue();
        assertThat(yn(global)).isEqualTo("NNYYN");
    }

    @Test
    @DisplayName("글로벌 upsertGlobal — 없으면 INSERT(tenant NULL, update_* NULL), 있으면 5 플래그 전부 갱신 + update_* 채움, 행은 1개")
    void upsertGlobal_insertsThenReplacesAllFlags() {
        jdbc.update("DELETE FROM tb_share_setting WHERE tenant_seq IS NULL"); // 시드 글로벌 행을 지우고 INSERT 경로부터
        loginAs(SUPER_ADMIN);
        repository.upsertGlobal(flags(null, true, true, true, true, true));
        ShareSettingData created = queryRepository.findGlobal();
        assertThat(created.updateUserSeq()).as("신규 행은 수정 감사 없음").isNull();
        assertThat(created.updateDate()).isNull();

        repository.upsertGlobal(flags(null, false, true, false, true, false));

        assertThat(count("tenant_seq IS NULL")).isEqualTo(1);
        ShareSettingData g = queryRepository.findGlobal();
        assertThat(g.isGlobal()).isTrue();
        assertThat(yn(g)).isEqualTo("NYNYN");
        assertThat(g.registerUserSeq()).as("SecurityContext 사용자로 채움").isEqualTo(SUPER_ADMIN);
        assertThat(g.updateUserSeq()).isEqualTo(SUPER_ADMIN);
        assertThat(g.updateDate()).as("충돌(갱신) 때만 채움").isNotNull();
    }

    @Test
    @DisplayName("글로벌 upsertGlobalEnforce — 기존 행의 is_enforced 만 바꾸고 상세 플래그는 보존, 신규면 엔티티 플래그로 생성")
    void upsertGlobalEnforce_touchesOnlyEnforce() {
        jdbc.update("DELETE FROM tb_share_setting WHERE tenant_seq IS NULL"); // 시드 글로벌 행을 지우고 신규 생성 경로부터
        loginAs(SUPER_ADMIN);
        repository.upsertGlobalEnforce(flags(null, false, false, true, true, false));
        assertThat(yn(queryRepository.findGlobal())).as("신규 생성은 엔티티 플래그 그대로").isEqualTo("NNYYN");

        repository.upsertGlobal(flags(null, false, true, true, false, true));
        repository.upsertGlobalEnforce(flags(null, true, false, false, false, false));

        assertThat(yn(queryRepository.findGlobal())).as("enforce 만 Y, 나머지 4개는 이전 upsertGlobal 값 보존").isEqualTo("YYYNY");
        assertThat(count("tenant_seq IS NULL")).isEqualTo(1);
    }

    @Test
    @DisplayName("글로벌 upsertGlobalShareOff — is_enforced + is_external 만 갱신, scope 3종 보존")
    void upsertGlobalShareOff_touchesEnforceAndExternal() {
        loginAs(SUPER_ADMIN);
        repository.upsertGlobal(flags(null, false, true, true, false, true));
        repository.upsertGlobalShareOff(flags(null, true, false, false, false, false));

        assertThat(yn(queryRepository.findGlobal())).isEqualTo("YNYNY");
    }

    @Test
    @DisplayName("테넌트 upsertTenant — is_enforced 제외 4 플래그 갱신, 행 1개 유지; upsertTenantShareOff — external 만 N")
    void upsertTenant_thenShareOff() {
        loginAs(ADMIN);
        repository.upsertTenant(flags(TENANT, false, true, false, false, true));

        assertThat(count("tenant_seq = " + TENANT)).isEqualTo(1);
        ShareSettingData t = queryRepository.findByTenant(TENANT);
        assertThat(yn(t)).isEqualTo("NYNNY");
        assertThat(t.registerUserSeq()).as("register 는 시드 값 보존").isEqualTo(SUPER_ADMIN);
        assertThat(t.updateUserSeq()).as("update 는 현재 사용자").isEqualTo(ADMIN);

        repository.upsertTenantShareOff(flags(TENANT, false, false, true, true, true));
        assertThat(yn(queryRepository.findByTenant(TENANT))).as("external 만 N, scope 3종 보존").isEqualTo("NNNNY");
    }

    @Test
    @DisplayName("insert(JPA persist) — 새 테넌트 기본 행 생성, 시큐리티 없으면 등록자는 선언 값·등록일은 Auditing 현재 시각(선언 값 무시), update_* 는 NULL")
    void insert_newTenant_auditingFillsRegisterAndLeavesUpdateNull() {
        Instant fixed = Instant.parse("2026-01-02T03:04:05.123456Z");
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ShareSetting e = ShareSetting.builder()
                .tenantSeq(NEW_TENANT).isEnforced(false).isExternalShareAllowed(false)
                .isSpecificUsersAllowed(true).isOrgLinkAllowed(true).isPublicAllowed(false)
                .registerUserSeq(ADMIN).registerDate(fixed)
                .build();
        repository.insert(e);

        ShareSettingData d = queryRepository.findByTenant(NEW_TENANT);
        assertThat(yn(d)).isEqualTo("NNYYN");
        assertThat(d.registerUserSeq()).isEqualTo(ADMIN);
        assertThat(d.registerDate()).as("@CreatedDate 가 선언 값을 덮어씀").isNotEqualTo(fixed).isAfterOrEqualTo(before);
        assertThat(d.registerDate().getNano()).as("초 단위 절삭").isZero();
        assertThat(d.updateUserSeq()).isNull();
        assertThat(d.updateDate()).isNull();
        assertThat(e.getShareSettingSeq()).as("identity 키 회수").isNotNull();

        // 방금 save 한 엔티티가 영속 컨텍스트에 남아 있어도 Query 리포는 생성자 식이라 같은 행의 JdbcClient upsert 뒤 DB 현재 값을 준다
        loginAs(ADMIN);
        repository.upsertTenant(flags(NEW_TENANT, false, true, false, false, true));
        assertThat(yn(queryRepository.findByTenant(NEW_TENANT))).as("옛 인스턴스가 아니라 DB 값").isEqualTo("NYNNY");
    }

    @Test
    @DisplayName("감사 자동 채움 — 엔티티에 날짜 없으면 현재 시각(초 절삭); SecurityContext 없으면 사용자 null → NOT NULL 위반(인터셉터 때와 동일)")
    void audit_fallbacks() {
        loginAs(ADMIN);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.insert(flags(NEW_TENANT, false, false, true, true, false));
        ShareSettingData d = queryRepository.findByTenant(NEW_TENANT);
        assertThat(d.registerDate()).isAfterOrEqualTo(before);
        assertThat(d.registerDate().getNano()).as("초 단위 절삭").isZero();
        assertThat(d.registerUserSeq()).isEqualTo(ADMIN);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> repository.insert(flags(NEW_TENANT + 1, false, false, true, true, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
