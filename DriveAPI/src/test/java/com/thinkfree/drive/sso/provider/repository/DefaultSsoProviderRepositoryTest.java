package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.query.*;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 SsoProvider 이관 회귀 — 읽기({@link DefaultSsoProviderQueryRepository}: 테넌트/글로벌 목록·단건·exists 3종 — 전부 JPQL DTO)·쓰기({@link DefaultSsoProviderRepository}: upsert 는 JdbcClient(PK 반환), IN 삭제는 JPQL).
 * 단건 INSERT·프로필 UPDATE·상태 전환은 호출부가 없어 W4(10-13) 에서 삭제 — 픽스처도 upsert.
 * 시드 = tenant 1("기본 테넌트"), user 1 superadmin(테넌트 없음)·2 admin(tenant 1). 트랜잭션 롤백.
 * 검증 포인트 = upsert(DEFAULT seq/명시 seq·충돌 갱신·PK 반환·write-back 없음·유니크 위반), 목록(seq DESC·커서·LIMIT·필터·조인 컬럼), 글로벌 목록(sort_order 순·tenant_name NULL), 활성 공급자 BASIC DTO, IN 삭제·빈 목록 가드.
 */
@DisplayName("DefaultSsoProviderRepository / DefaultSsoProviderQueryRepository — JPQL + JdbcClient upsert(PostgreSQL Testcontainers)")
class DefaultSsoProviderRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int TENANT = 1;

    @Autowired
    private SsoProviderRepository repository;
    @Autowired
    private SsoProviderQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
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

    private static SsoProvider.SsoProviderBuilder<?, ?> tenantProvider(String name, SsoProviderProtocolType type, SsoProviderStatus status) {
        return SsoProvider.builder().tenantSeq(TENANT).displayName(name).protocolType(type).status(status).scope(SsoProviderScope.TENANT).sortOrder(0);
    }

    private static SsoProvider.SsoProviderBuilder<?, ?> globalProvider(String name, SsoProviderProtocolType type, SsoProviderStatus status, int sortOrder) {
        return SsoProvider.builder().displayName(name).protocolType(type).status(status).scope(SsoProviderScope.GLOBAL).sortOrder(sortOrder);
    }

    /** 픽스처 — 단건 INSERT 경로는 W4 에서 삭제(호출부 0), upsert(seq 없음 = DEFAULT identity)로 만들고 회수한 PK 를 쓴다. */
    private int save(SsoProvider.SsoProviderBuilder<?, ?> builder) {
        return repository.upsert(builder.build());
    }

    private SsoProviderData findById(int seq) {
        return queryRepository.findById(SsoProviderFindQuery.builder().ssoProviderSeq(seq).build());
    }

    private String column(String col, int seq) {
        return jdbc.queryForObject("SELECT CAST(" + col + " AS VARCHAR) FROM tb_sso_provider WHERE sso_provider_seq = ?", String.class, seq);
    }

    private static List<Integer> seqs(List<SsoProviderData> rows) {
        return rows.stream().map(SsoProviderData::getSsoProviderSeq).toList();
    }

    @Test
    @DisplayName("upsert(DEFAULT seq) — identity 키·register_*(Instant)·findById 조인 컬럼 / findAll seq DESC·필터·커서·LIMIT / exists·findActiveByTenantAndProtocol, 유니크 위반은 맨 끝")
    void tenantScope() {
        Instant before = Instant.now().minusSeconds(1);
        int oidc1 = save(tenantProvider("oidc-1", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE));
        int oidc2 = save(tenantProvider("oidc-2", SsoProviderProtocolType.OIDC, SsoProviderStatus.INACTIVE));
        int jwt = save(tenantProvider("jwt-1", SsoProviderProtocolType.JWT, SsoProviderStatus.ACTIVE).registerUserSeq(SUPER_ADMIN));
        assertThat(oidc1).isPositive();
        assertThat(oidc2).isGreaterThan(oidc1);
        assertThat(findById(jwt).getRegisterUserSeq()).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);

        SsoProviderData found = findById(oidc1);
        assertThat(found.getDisplayName()).isEqualTo("oidc-1");
        assertThat(found.getTenantSeq()).isEqualTo(TENANT);
        assertThat(found.getTenantName()).isEqualTo("기본 테넌트");
        assertThat(found.getProtocolType()).isEqualTo(SsoProviderProtocolType.OIDC);
        assertThat(found.getStatus()).isEqualTo(SsoProviderStatus.ACTIVE);
        assertThat(found.getScope()).isEqualTo(SsoProviderScope.TENANT);
        assertThat(found.getSortOrder()).isZero();
        assertThat(found.getRegisterUserSeq()).isEqualTo(ADMIN);
        assertThat(found.getRegisterUserId()).isNotBlank();
        assertThat(found.getRegisterUserName()).isNotBlank();
        assertThat(found.getRegisterDate()).isNotNull().isAfter(before);
        assertThat(found.getUpdateUserSeq()).isNull();
        assertThat(found.getUpdateUserId()).isNull();
        assertThat(found.getUpdateDate()).isNull();
        assertThat(findById(999999)).isNull();

        // findAll — seq DESC, 테넌트·프로토콜 필터, page 없으면 LIMIT 없음
        List<SsoProviderData> all = queryRepository.findAll(SsoProviderListQuery.builder().tenantSeq(TENANT).build());
        assertThat(seqs(all)).containsExactly(jwt, oidc2, oidc1);
        assertThat(all.get(0).getTenantName()).isEqualTo("기본 테넌트");
        assertThat(seqs(queryRepository.findAll(SsoProviderListQuery.builder().tenantSeq(TENANT).protocolType(SsoProviderProtocolType.OIDC).build())))
                .containsExactly(oidc2, oidc1);
        assertThat(queryRepository.findAll(SsoProviderListQuery.builder().tenantSeq(TENANT + 1).build())).isEmpty();
        assertThat(seqs(queryRepository.findAll(SsoProviderListQuery.builder().build()))).as("조건 없음 = TENANT 범위 전체").contains(jwt, oidc1);
        // 커서·LIMIT
        List<SsoProviderData> page1 = queryRepository.findAll(SsoProviderListQuery.builder().tenantSeq(TENANT).page(CursorPaginationQuery.of(null, null, null, 2)).build());
        assertThat(seqs(page1)).containsExactly(jwt, oidc2);
        List<SsoProviderData> page2 = queryRepository.findAll(SsoProviderListQuery.builder().tenantSeq(TENANT).page(CursorPaginationQuery.of(null, (long) oidc2, null, 2)).build());
        assertThat(seqs(page2)).containsExactly(oidc1);

        // exists 2종 + 단건(소유 판정은 findById + Java — 서비스 단위 테스트)
        assertThat(queryRepository.existsActiveSingleType(SsoProviderActiveCheckQuery.builder().tenantSeq(TENANT).protocolType(SsoProviderProtocolType.OIDC).build())).isTrue();
        assertThat(queryRepository.existsActiveSingleType(SsoProviderActiveCheckQuery.builder().tenantSeq(TENANT).protocolType(SsoProviderProtocolType.SAML).build())).isFalse();
        assertThat(queryRepository.existsActiveSingleType(SsoProviderActiveCheckQuery.builder().tenantSeq(TENANT + 1).protocolType(SsoProviderProtocolType.OIDC).build())).isFalse();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.TENANT).tenantSeq(TENANT).displayName("oidc-1").build())).isTrue();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.TENANT).tenantSeq(TENANT).displayName("oidc-1").excludeSsoProviderSeq(oidc1).build())).as("자기 제외").isFalse();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.GLOBAL).displayName("oidc-1").build())).as("GLOBAL(tenant IS NULL)").isFalse();
        SsoProviderData active = queryRepository.findActiveByTenantAndProtocol(SsoProviderByProtocolQuery.builder().tenantSeq(TENANT).protocolType(SsoProviderProtocolType.OIDC).status(SsoProviderStatus.ACTIVE).build());
        assertThat(active.getSsoProviderSeq()).isEqualTo(oidc1);
        assertThat(active.getTenantName()).isEqualTo("기본 테넌트");
        assertThat(active.getRegisterUserSeq()).as("BASIC 9컬럼 — 감사 없음").isNull();
        assertThat(queryRepository.findActiveByTenantAndProtocol(SsoProviderByProtocolQuery.builder().tenantSeq(TENANT).protocolType(SsoProviderProtocolType.SAML).status(SsoProviderStatus.ACTIVE).build())).isNull();

        // 유니크 위반(scope, COALESCE(tenant,0), display_name)은 PG 트랜잭션을 abort 시키므로 맨 끝
        assertThatThrownBy(() -> save(tenantProvider("jwt-1", SsoProviderProtocolType.JWT, SsoProviderStatus.ACTIVE))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("GLOBAL 범위 — upsert(tenant NULL)·findAllGlobal sort_order·seq DESC(tenant_name NULL)·findById LEFT JOIN·findActiveByGlobalAndProtocol LIMIT 1·existsActiveSingleTypeByGlobal·existsDisplayName GLOBAL 분기")
    void globalScope() {
        int g2 = save(globalProvider("g-oidc-2", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE, 2));
        int g1 = save(globalProvider("g-oidc-1", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE, 1));
        int g1b = save(globalProvider("g-oidc-1b", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE, 1));
        int gJwt = save(globalProvider("g-jwt", SsoProviderProtocolType.JWT, SsoProviderStatus.INACTIVE, 0));
        save(tenantProvider("t-oidc", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE));

        List<SsoProviderData> all = queryRepository.findAllGlobal(SsoProviderGlobalListQuery.builder().build());
        assertThat(seqs(all)).as("sort_order ASC, 같으면 seq DESC").containsExactly(gJwt, g1b, g1, g2);
        assertThat(all.get(0).getTenantSeq()).isNull();
        assertThat(all.get(0).getTenantName()).isNull();
        assertThat(all.get(0).getRegisterUserId()).isNotBlank();
        assertThat(seqs(queryRepository.findAllGlobal(SsoProviderGlobalListQuery.builder().protocolType(SsoProviderProtocolType.JWT).build()))).containsExactly(gJwt);

        SsoProviderData found = findById(gJwt);
        assertThat(found.getScope()).isEqualTo(SsoProviderScope.GLOBAL);
        assertThat(found.getTenantName()).as("LEFT JOIN").isNull();
        assertThat(found.getRegisterUserName()).isNotBlank();

        SsoProviderData active = queryRepository.findActiveByGlobalAndProtocol(SsoProviderGlobalByProtocolQuery.builder().protocolType(SsoProviderProtocolType.OIDC).status(SsoProviderStatus.ACTIVE).build());
        assertThat(active).as("3건 중 LIMIT 1").isNotNull();
        assertThat(active.getSsoProviderSeq()).as("seq 가 가장 작은 것").isEqualTo(g2);
        assertThat(active.getSortOrder()).isEqualTo(2);
        assertThat(active.getRegisterUserSeq()).as("BASIC 9컬럼 — 감사 없음").isNull();
        assertThat(active.getScope()).isEqualTo(SsoProviderScope.GLOBAL);
        assertThat(active.getTenantName()).isNull();
        assertThat(queryRepository.findActiveByGlobalAndProtocol(SsoProviderGlobalByProtocolQuery.builder().protocolType(SsoProviderProtocolType.JWT).status(SsoProviderStatus.ACTIVE).build())).as("INACTIVE 만 있음").isNull();

        assertThat(queryRepository.existsActiveSingleTypeByGlobal(SsoProviderGlobalActiveCheckQuery.builder().protocolType(SsoProviderProtocolType.OIDC).build())).isTrue();
        assertThat(queryRepository.existsActiveSingleTypeByGlobal(SsoProviderGlobalActiveCheckQuery.builder().protocolType(SsoProviderProtocolType.JWT).build())).as("INACTIVE").isFalse();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.GLOBAL).displayName("g-jwt").build())).isTrue();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.GLOBAL).displayName("g-jwt").excludeSsoProviderSeq(gJwt).build())).isFalse();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.GLOBAL).displayName("t-oidc").build())).as("TENANT 범위 이름").isFalse();
        assertThat(queryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder().scope(SsoProviderScope.TENANT).tenantSeq(TENANT).displayName("g-jwt").build())).isFalse();
    }

    @Test
    @DisplayName("upsert — seq 없으면 DEFAULT(identity 를 RETURNING 으로 회수해 반환, 인자 엔티티는 안 고침), 같은 seq 재호출은 행 1개·값 갱신·update_* = 등록 감사 값 / deleteBySeqList·빈 목록 가드")
    void upsertAndDelete() {
        SsoProvider created = tenantProvider("up-1", SsoProviderProtocolType.OIDC, SsoProviderStatus.ACTIVE).sortOrder(1).build();
        Integer seq = repository.upsert(created);
        assertThat(seq).as("RETURNING 으로 회수").isNotNull().isPositive();
        assertThat(created.getSsoProviderSeq()).as("write-back 없음(W4)").isNull();
        assertThat(created.getRegisterUserSeq()).as("write-back 없음(W4)").isNull();
        assertThat(column("register_user_seq", seq)).as("컨텍스트 사용자").isEqualTo(String.valueOf(ADMIN));
        Instant registerDate = findById(seq).getRegisterDate();
        assertThat(registerDate).isNotNull();
        assertThat(column("update_user_seq", seq)).as("신규는 update_* 없음").isNull();

        SsoProvider again = tenantProvider("up-1-renamed", SsoProviderProtocolType.OIDC, SsoProviderStatus.INACTIVE).ssoProviderSeq(seq).iconImageKey("icon").sortOrder(7).registerUserSeq(SUPER_ADMIN).build();
        assertThat(repository.upsert(again)).as("충돌 행의 PK").isEqualTo(seq);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_sso_provider WHERE display_name LIKE 'up-1%'", Integer.class)).isEqualTo(1);
        SsoProviderData row = findById(seq);
        assertThat(row.getDisplayName()).isEqualTo("up-1-renamed");
        assertThat(row.getStatus()).isEqualTo(SsoProviderStatus.INACTIVE);
        assertThat(row.getIconImageKey()).isEqualTo("icon");
        assertThat(row.getSortOrder()).isEqualTo(7);
        assertThat(row.getRegisterUserSeq()).as("충돌 시 register_* 는 유지").isEqualTo(ADMIN);
        assertThat(row.getRegisterDate()).isEqualTo(registerDate);
        assertThat(row.getUpdateUserSeq()).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
        assertThat(row.getUpdateDate()).isNotNull().isAfterOrEqualTo(registerDate);

        // 명시 seq 가 없는 행이면 INSERT(identity 가 아닌 명시 값)
        SsoProvider explicit = tenantProvider("up-2", SsoProviderProtocolType.JWT, SsoProviderStatus.ACTIVE).ssoProviderSeq(seq + 1000).build();
        assertThat(repository.upsert(explicit)).isEqualTo(seq + 1000);
        assertThat(findById(seq + 1000).getDisplayName()).isEqualTo("up-2");

        assertThat(repository.deleteBySeqList(List.of())).isZero();
        assertThat(repository.deleteBySeqList(null)).isZero();
        assertThat(repository.deleteBySeqList(List.of(seq, seq + 1000, 999999))).isEqualTo(2);
        assertThat(findById(seq)).isNull();
        assertThat(findById(seq + 1000)).isNull();
    }
}
