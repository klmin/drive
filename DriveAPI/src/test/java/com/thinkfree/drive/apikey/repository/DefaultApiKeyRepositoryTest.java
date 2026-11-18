package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.user.enums.RoleType;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 ApiKey 이관(JdbcClient) 회귀. 시드 = tenant 1, user 1(superadmin, tenant NULL)·2(admin, tenant 1)·3(anonymous). tb_api_key 시드 없음. 트랜잭션 롤백.
 * 검증 포인트 = 목록 3종의 범위·조인 필드·is_expired(DB NOW())·커서, 조작 3종 × 범위 3종의 "범위 밖은 0", 인증 조회(ACTIVE·미만료·SUPER_ADMIN 예외),
 * Instant 왕복, identity 키 주입, last_used_date 자동 채움, IN 삭제·빈 목록 가드, 유니크 전파.
 */
@DisplayName("DefaultApiKeyRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultApiKeyRepositoryTest extends RepositoryTest {

    private static final int TENANT = 1;
    private static final int OTHER_TENANT = 2;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;
    private static final Instant FUTURE = Instant.parse("2030-01-02T03:04:05Z");
    private static final Instant PAST = Instant.parse("2020-01-02T03:04:05Z");

    @Autowired
    private ApiKeyRepository repository;
    @Autowired
    private ApiKeyQueryRepository queryRepository;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    @BeforeEach
    void loginAsAdmin() {
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

    /** converter.toSaveEntity 형태(status ACTIVE, 감사 없음 — registerUserSeq 는 컨텍스트에서). */
    private static ApiKey key(Integer tenantSeq, int userSeq, String raw, RoleType role, Instant expire) {
        return ApiKey.builder()
                .tenantSeq(tenantSeq).userSeq(userSeq).keyName("k-" + raw).keyPurpose("p").apiKey(raw)
                .roleType(role).scope(role == RoleType.USER ? "api:read api:write" : "").status(ApiKeyStatus.ACTIVE).expireDate(expire)
                .build();
    }

    private int insert(Integer tenantSeq, int userSeq, String raw, RoleType role, Instant expire) {
        ApiKey k = key(tenantSeq, userSeq, raw, role, expire);
        repository.insert(k);
        return k.getApiKeySeq();
    }

    private static ApiKeyListQuery list(Integer tenantSeq, Integer registerUserSeq, Long cursorId, int pageSize) {
        return ApiKeyListQuery.builder().tenantSeq(tenantSeq).registerUserSeq(registerUserSeq)
                .page(CursorPaginationQuery.of(null, cursorId, null, pageSize)).build();
    }

    private static List<Integer> seqs(List<ApiKeyData> rows) {
        return rows.stream().map(ApiKeyData::getApiKeySeq).toList();
    }

    private <T> T column(String col, int seq, Class<T> type) {
        return jdbc.queryForObject("SELECT " + col + " FROM tb_api_key WHERE api_key_seq = ?", type, seq);
    }

    private static void assertNotFound(Throwable ex) {
        assertThat(ex).isInstanceOf(ApiRuntimeException.class);
        assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(ApiKeyErrorCode.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("insert(save, flush 없이) — identity 키 주입·행이 JdbcTemplate 로 바로 보임, findById 13컬럼 단건 매핑(enum·Instant 왕복·scope), register 는 컨텍스트 사용자, last_used NULL")
    void insert_thenFindById() {
        int seq = insert(TENANT, ADMIN, "raw-1", RoleType.USER, FUTURE);
        assertThat(seq).isPositive();
        assertThat(column("api_key", seq, String.class)).as("IDENTITY 라 save 순간 INSERT").isEqualTo("raw-1");

        ApiKeyData d = queryRepository.findById(seq);
        assertThat(d).isNotNull();
        assertThat(d.getApiKeySeq()).isEqualTo(seq);
        assertThat(d.getTenantSeq()).isEqualTo(TENANT);
        assertThat(d.getUserSeq()).isEqualTo(ADMIN);
        assertThat(d.getKeyName()).isEqualTo("k-raw-1");
        assertThat(d.getKeyPurpose()).isEqualTo("p");
        assertThat(d.getApiKey()).isEqualTo("raw-1");
        assertThat(d.getRoleType()).isEqualTo(RoleType.USER);
        assertThat(d.getScope()).isEqualTo("api:read api:write");
        assertThat(d.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(d.getExpireDate()).as("Instant 왕복").isEqualTo(FUTURE);
        assertThat(d.getLastUsedDate()).isNull();
        assertThat(d.getRegisterUserSeq()).isEqualTo(ADMIN);
        assertThat(d.getRegisterDate()).isNotNull();
        assertThat(d.getIsExpired()).as("단건 조회는 is_expired 없음").isNull();
        assertThat(d.getUserId()).isNull();

        assertThat(queryRepository.findById(999_999)).isNull();
    }

    @Test
    @DisplayName("findAll(관리자) — 자기 테넌트의 ADMIN·USER 키만(SUPER_ADMIN·타 테넌트 제외), user 조인, is_expired 는 DB NOW() 비교, seq DESC + 커서")
    void findAll_adminScope() {
        int u1 = insert(TENANT, ADMIN, "a-1", RoleType.USER, FUTURE);
        int a1 = insert(TENANT, ADMIN, "a-2", RoleType.ADMIN, PAST);
        insert(null, SUPER_ADMIN, "a-3", RoleType.SUPER_ADMIN, FUTURE);
        insert(OTHER_TENANT, ANONYMOUS, "a-4", RoleType.USER, FUTURE);

        List<ApiKeyData> page1 = queryRepository.findAll(list(TENANT, null, null, 10));
        assertThat(seqs(page1)).containsExactly(a1, u1);
        ApiKeyData expired = page1.get(0);
        assertThat(expired.getIsExpired()).isTrue();
        assertThat(expired.getUserId()).isEqualTo("admin");
        assertThat(expired.getUserName()).isEqualTo("관리자");
        assertThat(expired.getTenantName()).as("관리자 목록은 tenant 조인 없음").isNull();
        assertThat(page1.get(1).getIsExpired()).isFalse();

        assertThat(seqs(queryRepository.findAll(list(TENANT, null, null, 1)))).containsExactly(a1);
        assertThat(seqs(queryRepository.findAll(list(TENANT, null, (long) a1, 10)))).containsExactly(u1);
        assertThat(queryRepository.findAll(list(TENANT, null, (long) u1, 10))).isEmpty();
    }

    @Test
    @DisplayName("findAllByRegisterUser(사용자) — 본인 발급 키만, 조인 필드 없음, is_expired 있음")
    void findAllByRegisterUser_ownKeysOnly() {
        int mine = insert(TENANT, ADMIN, "r-1", RoleType.USER, FUTURE);
        loginAs(SUPER_ADMIN);
        insert(TENANT, ADMIN, "r-2", RoleType.USER, FUTURE);

        List<ApiKeyData> rows = queryRepository.findAllByRegisterUser(list(null, ADMIN, null, 10));
        assertThat(seqs(rows)).containsExactly(mine);
        assertThat(rows.get(0).getIsExpired()).isFalse();
        assertThat(rows.get(0).getUserId()).isNull();
        assertThat(queryRepository.findAllByRegisterUser(list(null, ANONYMOUS, null, 10))).isEmpty();
    }

    @Test
    @DisplayName("findAllForSuperAdmin — 전체(tenant NULL 인 SUPER_ADMIN 키 포함, tenant LEFT JOIN), tenantSeq 주면 그 테넌트로, 커서")
    void findAllForSuperAdmin_allOrNarrowed() {
        int t1 = insert(TENANT, ADMIN, "s-1", RoleType.USER, FUTURE);
        int sa = insert(null, SUPER_ADMIN, "s-2", RoleType.SUPER_ADMIN, FUTURE);
        int t2 = insert(OTHER_TENANT, ANONYMOUS, "s-3", RoleType.USER, FUTURE);

        List<ApiKeyData> all = queryRepository.findAllForSuperAdmin(list(null, null, null, 10));
        assertThat(seqs(all)).containsExactly(t2, sa, t1);
        ApiKeyData superKey = all.get(1);
        assertThat(superKey.getTenantSeq()).isNull();
        assertThat(superKey.getTenantName()).as("LEFT JOIN — 슈퍼관리자 키도 목록에 남음").isNull();
        assertThat(superKey.getUserId()).isEqualTo("superadmin");
        assertThat(all.get(2).getTenantName()).isEqualTo("기본 테넌트");

        assertThat(seqs(queryRepository.findAllForSuperAdmin(list(TENANT, null, null, 10)))).containsExactly(t1);
        assertThat(seqs(queryRepository.findAllForSuperAdmin(list(null, null, (long) sa, 10)))).containsExactly(t1);
    }

    @Test
    @DisplayName("관리자·슈퍼관리자 목록의 registerUserSeq(mine) — 본인 발급 키로 좁히고 조인 필드는 그대로, 슈퍼관리자는 tenantSeq 와 AND")
    void findAll_registerUserNarrowed() {
        int byAdmin = insert(TENANT, ADMIN, "m-1", RoleType.USER, FUTURE);
        loginAs(SUPER_ADMIN);
        int bySuperToTenant = insert(TENANT, ADMIN, "m-2", RoleType.ADMIN, FUTURE);
        int bySuperSelf = insert(null, SUPER_ADMIN, "m-3", RoleType.SUPER_ADMIN, FUTURE);

        List<ApiKeyData> adminMine = queryRepository.findAll(list(TENANT, ADMIN, null, 10));
        assertThat(seqs(adminMine)).containsExactly(byAdmin);
        assertThat(adminMine.get(0).getUserId()).isEqualTo("admin");

        List<ApiKeyData> superMine = queryRepository.findAllForSuperAdmin(list(null, SUPER_ADMIN, null, 10));
        assertThat(seqs(superMine)).containsExactly(bySuperSelf, bySuperToTenant);
        assertThat(superMine.get(1).getTenantName()).isEqualTo("기본 테넌트");
        assertThat(seqs(queryRepository.findAllForSuperAdmin(list(TENANT, SUPER_ADMIN, null, 10)))).containsExactly(bySuperToTenant);
    }

    @Test
    @DisplayName("슈퍼관리자 보호 설정·해제 — true 설정·false 해제·같은 값 재요청 허용, 만료일·다른 키는 그대로, 만료일 수정은 보호 여부를 바꾸지 않음, 없는 키 NOT_FOUND")
    void superAdminUpdatesProtection() {
        int target = insert(TENANT, ADMIN, "p-1", RoleType.USER, FUTURE);
        int other = insert(TENANT, ADMIN, "p-2", RoleType.USER, FUTURE);

        apiKeyService.updateProtectionForSuperAdmin(target, true);
        em.flush();
        assertThat(column("is_protected", target, String.class)).as("설정").isEqualTo("Y");
        assertThat(column("expire_date", target, OffsetDateTime.class).toInstant()).as("만료일 그대로").isEqualTo(FUTURE);
        assertThat(column("is_protected", other, String.class)).as("다른 키는 그대로").isEqualTo("N");
        assertThat(queryRepository.findById(target).getIsProtected()).as("조회 DTO 도 보호").isTrue();

        apiKeyService.updateProtectionForSuperAdmin(target, true);
        apiKeyService.updateExpireDateForSuperAdmin(target, PAST);
        em.flush();
        assertThat(column("is_protected", target, String.class)).as("같은 값 재요청·만료일 수정 뒤에도 보호 유지").isEqualTo("Y");

        apiKeyService.updateProtectionForSuperAdmin(target, false);
        em.flush();
        assertThat(column("is_protected", target, String.class)).as("해제").isEqualTo("N");
        assertThat(queryRepository.findById(target).getIsProtected()).isFalse();

        assertThatThrownBy(() -> apiKeyService.updateProtectionForSuperAdmin(Integer.MAX_VALUE, true)).as("없는 키")
                .satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
    }

    @Test
    @DisplayName("조작 범위(W5 — Service 가 findById 1회 로드 + Java 판정 + 변경 감지) — ByTenant 는 타 테넌트·SUPER_ADMIN role 에 NOT_FOUND, ByRegisterUser 는 타 발급자에 NOT_FOUND, ForSuperAdmin 은 전부, 없는 키 NOT_FOUND")
    void mutationScopes() {
        int userKey = insert(TENANT, ADMIN, "m-1", RoleType.USER, FUTURE);
        int superKey = insert(null, SUPER_ADMIN, "m-2", RoleType.SUPER_ADMIN, FUTURE);

        // 만료일 — UPDATE 는 flush(커밋) 때 나간다: flush 전 DB 는 옛 값, flush 후 새 값
        apiKeyService.updateExpireDateByTenant(userKey, TENANT, PAST);
        assertThat(column("expire_date", userKey, OffsetDateTime.class).toInstant()).as("flush 전 — 변경 감지는 아직 미반영").isEqualTo(FUTURE);
        em.flush();
        assertThat(column("expire_date", userKey, OffsetDateTime.class).toInstant()).as("flush 후 반영").isEqualTo(PAST);
        assertThat(column("update_user_seq", userKey, Integer.class)).as("수정 감사 = 시큐리티 사용자").isEqualTo(ADMIN);
        assertThat(column("update_date", userKey, OffsetDateTime.class)).isNotNull();
        assertThatThrownBy(() -> apiKeyService.updateExpireDateByTenant(userKey, OTHER_TENANT, FUTURE)).as("타 테넌트").satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
        assertThatThrownBy(() -> apiKeyService.updateExpireDateByTenant(superKey, null, PAST)).as("SUPER_ADMIN 은 tenant NULL 이라 매치 불가").satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
        assertThatThrownBy(() -> apiKeyService.updateExpireDateByRegisterUser(userKey, SUPER_ADMIN, FUTURE)).as("타 발급자").satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
        em.flush();
        assertThat(column("expire_date", userKey, OffsetDateTime.class).toInstant()).as("범위 밖 요청은 바꾸지 않음").isEqualTo(PAST);
        apiKeyService.updateExpireDateByRegisterUser(userKey, ADMIN, FUTURE);
        apiKeyService.updateExpireDateForSuperAdmin(superKey, PAST);
        assertThat(queryRepository.findById(userKey).getExpireDate()).as("JPQL DTO 조회 직전 자동 flush").isEqualTo(FUTURE);
        assertThat(queryRepository.findById(superKey).getExpireDate()).isEqualTo(PAST);

        // 상태
        apiKeyService.updateStatusByTenant(userKey, TENANT, ApiKeyStatus.INACTIVE);
        assertThatThrownBy(() -> apiKeyService.updateStatusByRegisterUser(userKey, ANONYMOUS, ApiKeyStatus.ACTIVE)).satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
        apiKeyService.updateStatusForSuperAdmin(superKey, ApiKeyStatus.INACTIVE);
        assertThat(queryRepository.findById(userKey).getStatus()).isEqualTo(ApiKeyStatus.INACTIVE);
        assertThat(queryRepository.findById(superKey).getStatus()).isEqualTo(ApiKeyStatus.INACTIVE);

        // 재발급 — 키 문자열 교체 + ACTIVE 복귀 + update 감사
        loginAs(SUPER_ADMIN);
        String newRaw = apiKeyService.reissueByTenant(userKey, TENANT);
        ApiKeyData reissued = queryRepository.findById(userKey);
        assertThat(reissued.getApiKey()).isEqualTo(newRaw).isNotEqualTo("m-1");
        assertThat(reissued.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(column("update_user_seq", userKey, Integer.class)).isEqualTo(SUPER_ADMIN);
        assertThatThrownBy(() -> apiKeyService.reissueByRegisterUser(userKey, SUPER_ADMIN)).satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
        assertThat(apiKeyService.reissueForSuperAdmin(superKey)).isEqualTo(queryRepository.findById(superKey).getApiKey());
        assertThatThrownBy(() -> apiKeyService.updateStatusForSuperAdmin(999_999, ApiKeyStatus.ACTIVE)).as("없는 키").satisfies(DefaultApiKeyRepositoryTest::assertNotFound);
    }

    @Test
    @DisplayName("findActiveByApiKey(인증) — 같은 테넌트 ACTIVE 미만료만; 타 테넌트·INACTIVE·만료는 null; SUPER_ADMIN 키는 어느 테넌트에서도")
    void findActiveByApiKey_authRules() {
        int active = insert(TENANT, ADMIN, "auth-ok", RoleType.USER, FUTURE);
        insert(TENANT, ADMIN, "auth-expired", RoleType.USER, PAST);
        int inactive = insert(TENANT, ADMIN, "auth-inactive", RoleType.USER, FUTURE);
        apiKeyService.updateStatusForSuperAdmin(inactive, ApiKeyStatus.INACTIVE);
        insert(null, SUPER_ADMIN, "auth-super", RoleType.SUPER_ADMIN, FUTURE);

        assertThat(queryRepository.findActiveByApiKey("auth-ok", TENANT).getApiKeySeq()).isEqualTo(active);
        assertThat(queryRepository.findActiveByApiKey("auth-ok", OTHER_TENANT)).as("타 테넌트 호스트").isNull();
        assertThat(queryRepository.findActiveByApiKey("auth-ok", null)).as("tenant NULL 바인딩(Types.INTEGER) — 일반 키는 매치 안 됨").isNull();
        assertThat(queryRepository.findActiveByApiKey("auth-expired", TENANT)).isNull();
        assertThat(queryRepository.findActiveByApiKey("auth-inactive", TENANT)).isNull();
        assertThat(queryRepository.findActiveByApiKey("auth-super", TENANT)).isNotNull();
        assertThat(queryRepository.findActiveByApiKey("auth-super", OTHER_TENANT)).isNotNull();
        assertThat(queryRepository.findActiveByApiKey("auth-super", null)).isNotNull();
        assertThat(queryRepository.findActiveByApiKey("nope", TENANT)).isNull();

        // 만료 연장 → 즉시 부활(배치 없음)
        int expiredSeq = queryRepository.findAllByRegisterUser(list(null, ADMIN, null, 10)).stream()
                .filter(k -> "auth-expired".equals(k.getApiKey())).findFirst().orElseThrow().getApiKeySeq();
        apiKeyService.updateExpireDateForSuperAdmin(expiredSeq, FUTURE);
        assertThat(queryRepository.findActiveByApiKey("auth-expired", TENANT)).isNotNull();
    }

    @Test
    @DisplayName("updateLastUsedDate(JPQL @Modifying 유지) — 현재 시각(초 절삭), 감사 컬럼은 건드리지 않음")
    void updateLastUsedDate_fillsNow() {
        int seq = insert(TENANT, ADMIN, "lu-1", RoleType.USER, FUTURE);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        repository.updateLastUsedDate("lu-1");
        Instant stored = column("last_used_date", seq, OffsetDateTime.class).toInstant();
        assertThat(stored).isAfterOrEqualTo(before);
        assertThat(stored.getNano()).isZero();
        assertThat(column("update_date", seq, OffsetDateTime.class)).as("수정일은 그대로(NULL)").isNull();
        assertThat(queryRepository.findById(seq).getLastUsedDate()).isNotNull();
    }

    @Test
    @DisplayName("deleteByUserSeqList — IN 바인딩으로 대상 사용자 키만 삭제, 빈 목록·null 은 0(SQL 미실행)")
    void deleteByUserSeqList() {
        insert(TENANT, ADMIN, "d-1", RoleType.USER, FUTURE);
        insert(TENANT, ADMIN, "d-2", RoleType.USER, FUTURE);
        int keep = insert(TENANT, ANONYMOUS, "d-3", RoleType.USER, FUTURE);

        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(ADMIN, 999_999))).isEqualTo(2);
        assertThat(seqs(queryRepository.findAllForSuperAdmin(list(null, null, null, 10)))).containsExactly(keep);
    }

    @Test
    @DisplayName("감사·제약 — 엔티티 register 값 우선, 컨텍스트 없으면 NOT NULL 위반, api_key 유니크 위반 전파")
    void auditAndConstraints() {
        Instant fixed = Instant.parse("2026-01-02T03:04:05Z");
        ApiKey k = ApiKey.builder().tenantSeq(TENANT).userSeq(ADMIN).keyName("au").keyPurpose("p").apiKey("au-1")
                .roleType(RoleType.USER).scope("").status(ApiKeyStatus.ACTIVE).expireDate(FUTURE)
                .registerUserSeq(SUPER_ADMIN).registerDate(fixed).build();
        repository.insert(k);
        assertThat(jdbc.queryForObject("SELECT register_user_seq FROM tb_api_key WHERE api_key_seq = ?", Integer.class, k.getApiKeySeq())).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
        assertThat(jdbc.queryForObject("SELECT register_date FROM tb_api_key WHERE api_key_seq = ?", java.time.OffsetDateTime.class, k.getApiKeySeq()).toInstant()).as("JPA Auditing 이 등록일을 현재 시각으로 덮어씀").isNotEqualTo(fixed);

        assertThatThrownBy(() -> insert(TENANT, ADMIN, "au-1", RoleType.USER, FUTURE))
                .as("uniq_api_key").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("감사 — SecurityContext 없으면 register_user_seq null → NOT NULL 위반(인터셉터 때와 동일)")
    void insert_withoutContext_fails() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> insert(TENANT, ADMIN, "no-ctx", RoleType.USER, FUTURE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
