package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.JdbcStatementCounter;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.data.UserBulkStatusData;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserSearchData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.*;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.query.*;
import jakarta.persistence.EntityManager;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 User 이관 회귀 — 읽기({@link DefaultUserQueryRepository}: JPA 단건 8 + JdbcClient 16)·쓰기({@link DefaultUserRepository}: JdbcClient 22).
 * 시드 = tenant 1, user 1 superadmin(tenant NULL)·2 admin(tenant 1)·3 anonymous(tenant NULL), 전부 ACTIVE. 트랜잭션 롤백.
 * 시트 검사 INSERT 는 {@code SeatInsertCheckRepositoryTest} 가 따로 다룬다.
 */
@DisplayName("DefaultUserRepository / DefaultUserQueryRepository — JPA + JdbcClient(PostgreSQL Testcontainers)")
class DefaultUserRepositoryTest extends RepositoryTest {

    private static final int TENANT = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int ANONYMOUS = 3;

    @Autowired
    private UserRepository repository;
    @Autowired
    private UserQueryRepository queryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserJpaRepository userJpaRepository;
    @Autowired
    private JdbcClient jdbcClient;

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

    private int insert(String userId, String userName, UserStatus status, RoleType role, Integer tenantSeq) {
        User u = User.createUser(userId, tenantSeq, null, SignupType.LOCAL, userName, status, 0L, SUPER_ADMIN, null, null, null);
        assertThat(repository.insertUserWithSeatCheck(u, false, 0)).isTrue();
        // 운영의 계정 생성 경로와 같이 역할 행도 — 로그인 계열 조회는 tb_user_role 을 본다(다중 role, 10-23)
        jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, ?, ?)", u.getUserSeq(), role.name(), SUPER_ADMIN);
        return u.getUserSeq();
    }

    private int insertUser(String userId, String userName, UserStatus status) {
        return insert(userId, userName, status, RoleType.USER, TENANT);
    }

    /** 엔티티 왕복 확인용(테스트 전용) — 구 UserQueryRepository.findByUserSeq 와 같은 PK 로드 + detach(Query 리포는 10-12 부터 엔티티를 반환하지 않음) */
    private User loadEntity(Integer userSeq) {
        if (userSeq == null) {
            return null;
        }
        User u = entityManager.find(User.class, userSeq);
        if (u != null) {
            entityManager.detach(u);
        }
        return u;
    }

    /** 변경 감지 결과는 DB 값으로 확인 — 미반영 변경을 flush(커밋 대신) 후 JdbcTemplate */
    private <T> T col(int userSeq, String column, Class<T> type) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT " + column + " FROM tb_user WHERE user_seq = ?", type, userSeq);
    }

    /** 탈퇴(삭제 대기) — 쓰기 리포 findById 관리 엔티티 + {@link User#withdraw} 변경 감지(W7) */
    private void withdrawUser(int userSeq, Instant pendingDeleteDate, Instant scheduleTime) {
        assertThat(repository.findById(userSeq).withdraw(pendingDeleteDate, scheduleTime)).isTrue();
        entityManager.flush();
    }

    private static CursorPaginationQuery page(Long cursorId, Object cursorValue, int pageSize) {
        return CursorPaginationQuery.of(cursorValue, cursorId, null, pageSize);
    }

    private static UserListQuery listQuery(UserStatus status, String userId, Long cursorId, int pageSize) {
        return listQuery(List.of(RoleType.USER), status, userId, cursorId, pageSize);
    }

    private static UserListQuery listQuery(List<RoleType> roles, UserStatus status, String userId, Long cursorId, int pageSize) {
        return UserListQuery.builder().tenantSeq(TENANT).roles(roles).status(status).userId(userId).page(page(cursorId, null, pageSize)).build();
    }

    // ================================================================== 읽기 — JPA 단건

    @Nested
    @DisplayName("엔티티 매핑·로그인용 단건 조회(인증 DTO)")
    class JpaLookups {

        @Test
        @DisplayName("엔티티 PK 로드 — 전체 컬럼·컨버터(Password·Y/N·UserLanguage·DefaultLandingPage·enum·LocalDateTime) 매핑")
        void findByUserSeq() {
            User admin = loadEntity(ADMIN);
            assertThat(admin).isNotNull();
            assertThat(admin.getUserId()).isEqualTo("admin");
            assertThat(admin.getEncPwd()).isNotNull();
            assertThat(admin.getEncPwd().getValue()).startsWith("{bcrypt}");
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.getSignupType()).isEqualTo(SignupType.LOCAL);
            assertThat(admin.getUserLanguage()).isEqualTo(UserLanguage.AUTO);
            assertThat(admin.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.HOME);
            assertThat(admin.getMfaStatus()).isEqualTo(MfaStatus.DISABLED);
            assertThat(admin.getMfaMethod()).isNull();
            assertThat(admin.getTenantSeq()).isEqualTo(TENANT);
            assertThat(admin.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(admin.getRegisterDate()).isNotNull();
            assertThat(admin.getLoginFailureCount()).isZero();
            assertThat(admin.getTotalQuotaByte()).isZero();
            assertThat(loadEntity(99999)).isNull();
            assertThat(loadEntity(null)).isNull();
        }

        @Test
        @DisplayName("role 별 아이디 조회(인증 DTO) — USER/ADMIN/SUPER_ADMIN 은 서로 섞이지 않고 ACTIVE 만")
        void findByIdAndRole() {
            int active = insertUser("u-active", "활성", UserStatus.ACTIVE);
            insertUser("u-inactive", "비활성", UserStatus.INACTIVE);

            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "u-active", RoleType.USER).getUserSeq()).isEqualTo(active);
            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "u-inactive", RoleType.USER)).isNull();
            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "admin", RoleType.USER)).as("ADMIN 은 USER 조회에 안 잡힘").isNull();
            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "admin", RoleType.ADMIN).getUserSeq()).isEqualTo(ADMIN);
            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "superadmin", RoleType.ADMIN)).isNull();
            assertThat(queryRepository.findActiveSuperAdminAuthByUserId("superadmin").getUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(queryRepository.findActiveSuperAdminAuthByUserId("admin")).isNull();

            // userId 는 테넌트 안에서만 유일 — 다른 테넌트 조회엔 안 잡히고, 슈퍼관리자 조회는 테넌트 없는 계정만
            assertThat(queryRepository.findActiveAuthByUserId(TENANT + 1, "u-active", RoleType.USER)).as("다른 테넌트").isNull();
            assertThat(queryRepository.findActiveAuthByUserId(TENANT + 1, "admin", RoleType.ADMIN)).as("다른 테넌트").isNull();
            insert("sa-in-tenant", "테넌트SA", UserStatus.ACTIVE, RoleType.SUPER_ADMIN, TENANT);
            assertThat(queryRepository.findActiveSuperAdminAuthByUserId("sa-in-tenant")).as("tenant_seq 있는 SUPER_ADMIN 보유 계정은 제외").isNull();
        }

        @Test
        @DisplayName("findActiveDetailByUserSeq·findAnonymousUser·actor 매핑(earliest SUPER_ADMIN)")
        void activeAndActorLookups() {
            assertThat(queryRepository.findActiveDetailByUserSeq(ADMIN).getUserId()).isEqualTo("admin");
            assertThat(queryRepository.findActiveDetailByUserSeq(99999)).isNull();

            UserDetailData anonymous = queryRepository.findAnonymousUser();
            assertThat(anonymous.getUserSeq()).isEqualTo(ANONYMOUS);

            UserDetailData earliest = queryRepository.findEarliestActiveSuperAdmin();
            assertThat(earliest.getUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(earliest.getUserId()).isEqualTo("superadmin");
        }
    }

    // ================================================================== 읽기 — DTO 단건(JPQL 생성자 식, 10-12 단계 3)

    @Nested
    @DisplayName("DTO 단건 조회(JPQL 생성자 식 — 계정 값·인증)")
    class DtoLookups {

        @Test
        @DisplayName("계정 값 DTO — 19필드·컨버터(Y/N·UserLanguage·DefaultLandingPage), 비밀값 없음. PK 조회는 상태 무관, ACTIVE 판정은 default 메서드")
        void detail() {
            int inactive = insertUser("d-inactive", "비활성", UserStatus.INACTIVE);
            User entity = loadEntity(ADMIN);

            UserDetailData admin = queryRepository.findDetailByUserSeq(ADMIN);
            assertThat(admin.getUserSeq()).isEqualTo(ADMIN);
            assertThat(admin.getUserId()).isEqualTo("admin");
            assertThat(admin.getUserName()).isEqualTo(entity.getUserName());
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.getTotalQuotaByte()).isEqualTo(entity.getTotalQuotaByte());
            assertThat(admin.getUseQuotaByte()).isEqualTo(entity.getUseQuotaByte());
            assertThat(admin.getRegisterUserSeq()).isEqualTo(SUPER_ADMIN);
            assertThat(admin.getRegisterDate()).isEqualTo(entity.getRegisterDate());
            assertThat(admin.getUpdateUserSeq()).isEqualTo(entity.getUpdateUserSeq());
            assertThat(admin.getUpdateDate()).isEqualTo(entity.getUpdateDate());
            assertThat(admin.getUserLanguage()).isEqualTo(UserLanguage.AUTO);
            assertThat(admin.getCountryCode()).isEqualTo(entity.getCountryCode());
            assertThat(admin.getPhoneNumber()).isEqualTo(entity.getPhoneNumber());
            assertThat(admin.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.HOME);
            assertThat(admin.getProfileImageKey()).isEqualTo(entity.getProfileImageKey());
            assertThat(admin.getTenantSeq()).isEqualTo(TENANT);
            assertThat(admin.getMfaStatus()).isEqualTo(MfaStatus.DISABLED);
            assertThat(queryRepository.findDetailByUserSeq(inactive)).as("PK 조회는 상태 무관").isNotNull();
            assertThat(queryRepository.findDetailByUserSeq(99999)).isNull();
            assertThat(queryRepository.findDetailByUserSeq(null)).isNull();

            assertThat(queryRepository.findActiveDetailByUserSeq(ADMIN).getUserId()).isEqualTo("admin");
            assertThat(queryRepository.findActiveDetailByUserSeq(inactive)).as("ACTIVE 만(Java 판정)").isNull();
            assertThat(queryRepository.findActiveDetailByUserSeq(99999)).isNull();
            assertThat(queryRepository.findActiveDetailByUserSeq(null)).isNull();
        }

        @Test
        @DisplayName("계정 값 + 보유 역할(jOOQ 1회) — 계정 값 19필드가 JPQL findDetailByUserSeq 와 같고, roles 는 보유 역할 전부(enum 순서). 상태 무관·ACTIVE 판정은 default 메서드")
        void detailWithRoles() {
            int both = insertUser("dr-both", "둘다", UserStatus.ACTIVE);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'ADMIN', ?)", both, SUPER_ADMIN);
            jdbc.update("UPDATE tb_user SET country_code = '82', phone_number = '01012345678', user_language = 'ko', default_landing_page = 'my-drive' WHERE user_seq = ?", both);
            int inactive = insertUser("dr-inactive", "비활성", UserStatus.INACTIVE);

            for (int seq : new int[]{ADMIN, both, inactive}) {
                UserDetailWithRolesData withRoles = queryRepository.findDetailWithRoles(seq);
                assertThat(withRoles).as("계정 값은 JPQL 조회와 같다 — seq %d", seq)
                        .usingRecursiveComparison().ignoringFields("roles", "isProtected")
                        .isEqualTo(queryRepository.findDetailByUserSeq(seq));
            }
            assertThat(queryRepository.findDetailWithRoles(ADMIN).getIsProtected()).as("설치 시드 관리자는 보호 계정").isTrue();
            assertThat(queryRepository.findDetailWithRoles(both).getIsProtected()).as("일반 등록 계정은 보호 아님").isFalse();
            assertThat(queryRepository.findDetailWithRoles(both).getRoles()).containsExactly(RoleType.ADMIN, RoleType.USER);
            assertThat(queryRepository.findDetailWithRoles(both).getUserLanguage()).isEqualTo(UserLanguage.KO);
            assertThat(queryRepository.findDetailWithRoles(both).getDefaultLandingPage()).isEqualTo(DefaultLandingPage.MY_DRIVE);
            assertThat(queryRepository.findDetailWithRoles(ADMIN).getRoles()).containsExactly(RoleType.ADMIN);
            assertThat(queryRepository.findDetailWithRoles(99999)).isNull();

            assertThat(queryRepository.findActiveDetailWithRoles(both).getRoles()).containsExactly(RoleType.ADMIN, RoleType.USER);
            assertThat(queryRepository.findActiveDetailWithRoles(inactive)).as("ACTIVE 만").isNull();
        }

        @Test
        @DisplayName("userId 조회(WHERE 로 ACTIVE·USER role) — 계정 값 DTO, 비활성·다른 role 은 null")
        void activeDetailByUserId() {
            int active = insertUser("p-active", "활성", UserStatus.ACTIVE);
            insertUser("p-inactive", "비활성", UserStatus.INACTIVE);

            assertThat(queryRepository.findActiveDetailByUserId(TENANT, "p-active").getUserSeq()).isEqualTo(active);
            assertThat(queryRepository.findActiveDetailByUserId(TENANT, "p-inactive")).isNull();
            assertThat(queryRepository.findActiveDetailByUserId(TENANT, "admin")).as("USER role 만").isNull();
            assertThat(queryRepository.findActiveDetailByUserId(TENANT + 1, "p-active")).as("다른 테넌트").isNull();
        }

        @Test
        @DisplayName("인증 DTO — 계정 값 + 비밀번호(Password 컨버터)·로그인 실패 수·MFA, PK 조회는 상태 무관")
        void auth() {
            int inactive = insertUser("a-inactive", "비활성", UserStatus.INACTIVE);

            UserAuthData admin = queryRepository.findAuthByUserSeq(ADMIN);
            UserDetailData detail = queryRepository.findDetailByUserSeq(ADMIN);
            assertThat(admin.getUserSeq()).isEqualTo(ADMIN);
            assertThat(admin.getUserId()).isEqualTo("admin");
            assertThat(admin.getTenantSeq()).isEqualTo(TENANT);
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.getUserLanguage()).isEqualTo(UserLanguage.AUTO);
            assertThat(admin.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.HOME);
            assertThat(admin.getTotalQuotaByte()).isEqualTo(detail.getTotalQuotaByte());
            assertThat(admin.getRegisterDate()).as("계정 값 필드는 상세 DTO 와 같음").isEqualTo(detail.getRegisterDate());
            assertThat(admin.getEncPwd().getValue()).startsWith("{bcrypt}");
            assertThat(admin.getLoginFailureCount()).isZero();
            assertThat(admin.getMfaStatus()).isEqualTo(MfaStatus.DISABLED);
            assertThat(admin.getMfaMethod()).isNull();
            assertThat(admin.getMfaSecretKey()).isNull();
            assertThat(queryRepository.findAuthByUserSeq(inactive)).as("PK 조회는 상태 무관").isNotNull();
            assertThat(queryRepository.findAuthByUserSeq(99999)).isNull();
        }

        @Test
        @DisplayName("비활성 계정도 PK 조회로 용량을 읽는다(용량 알림·사용량 지표), ACTIVE 용 findUserQuotaByUserSeq 는 null")
        void quotaOfInactiveUser() {
            int inactive = insertUser("q-inactive", "비활성", UserStatus.INACTIVE);
            repository.addUseQuotaByte(inactive, 42L);

            assertThat(queryRepository.findDetailByUserSeq(inactive).getUseQuotaByte()).isEqualTo(42L);
            assertThat(queryRepository.findDetailByUserSeq(inactive).getTotalQuotaByte()).isZero();
            assertThat(queryRepository.findUserQuotaByUserSeq(inactive)).as("ACTIVE 용은 그대로").isNull();
        }

        @Test
        @DisplayName("DTO 조회는 같은 트랜잭션의 관리 엔티티를 떼어내지 않고 DB 현재 값 — @Modifying 으로 바뀐 사용량을 본다(용량 알림 재조회 H5)")
        void dtoReadsDbValueWithoutTouchingManagedEntity() {
            int u = insertUser("m-user", "관리", UserStatus.ACTIVE);
            User managed = entityManager.find(User.class, u);
            assertThat(managed.getUseQuotaByte()).isZero();

            repository.addUseQuotaByte(u, 500L);

            assertThat(queryRepository.findDetailByUserSeq(u).getUseQuotaByte()).as("DB 현재 값").isEqualTo(500L);
            assertThat(queryRepository.findActiveDetailByUserSeq(u).getUseQuotaByte()).isEqualTo(500L);
            assertThat(queryRepository.findAuthByUserSeq(u).getUseQuotaByte()).isEqualTo(500L);
            assertThat(entityManager.contains(managed)).as("DTO 조회가 관리 엔티티를 떼어내지 않음").isTrue();
        }
    }

    // ================================================================== 읽기 — 스칼라

    @Nested
    @DisplayName("스칼라 조회")
    class Scalars {

        @Test
        @DisplayName("tenantSeq·테넌트 소속 count·존재 아이디·quota")
        void scalars() {
            assertThat(queryRepository.findTenantSeqByUserSeq(ADMIN)).isEqualTo(TENANT);
            assertThat(queryRepository.findTenantSeqByUserSeq(SUPER_ADMIN)).as("tenant NULL 행").isNull();
            assertThat(queryRepository.findTenantSeqByUserSeq(99999)).as("없는 행").isNull();

            assertThat(queryRepository.countByUserSeqsAndTenant(List.of(SUPER_ADMIN, ADMIN, ANONYMOUS), TENANT)).isEqualTo(1);
            assertThat(queryRepository.countByUserSeqsAndTenant(List.of(), TENANT)).as("빈 목록 가드").isZero();

            assertThat(queryRepository.findExistingUserIds(TENANT, List.of("admin", "nope", "superadmin"))).as("같은 테넌트만").containsExactly("admin");
            assertThat(queryRepository.findExistingUserIds(null, List.of("admin", "nope", "superadmin"))).as("null = 테넌트 없는 계정끼리").containsExactly("superadmin");
            assertThat(queryRepository.findExistingUserIds(TENANT + 1, List.of("admin"))).as("다른 테넌트").isEmpty();
            assertThat(queryRepository.findExistingUserIds(TENANT, List.of())).isEmpty();

            var quota = queryRepository.findUserQuotaByUserSeq(ADMIN);
            assertThat(quota.getTotalQuotaByte()).isZero();
            assertThat(quota.getUseQuotaByte()).isZero();
            assertThat(queryRepository.findUserQuotaByUserSeq(99999)).isNull();

            assertThat(queryRepository.existsUserId(TENANT, "admin")).isTrue();
            assertThat(queryRepository.existsUserId(TENANT, "nope")).isFalse();
            assertThat(queryRepository.existsUserId(TENANT + 1, "admin")).as("다른 테넌트").isFalse();
            assertThat(queryRepository.existsUserId(null, "superadmin")).as("테넌트 없는 계정끼리").isTrue();
            assertThat(queryRepository.existsUserId(TENANT, "superadmin")).isFalse();
        }
    }

    // ================================================================== 읽기 — 목록 DTO

    @Nested
    @DisplayName("목록 DTO")
    class Lists {

        @Test
        @DisplayName("findDisplayableUsers(USER) — 상태 기본(ACTIVE+INACTIVE)·status 지정·userId 부분 일치·user_seq 커서·최근 로그인 조인")
        void displayableUsers() {
            int u1 = insertUser("list-a", "에이", UserStatus.ACTIVE);
            int u2 = insertUser("list-b", "비", UserStatus.INACTIVE);
            int u3 = insertUser("list-c", "씨", UserStatus.ACTIVE);
            withdrawUser(insertUser("list-d", "디", UserStatus.ACTIVE), Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            jdbc.update("INSERT INTO tb_user_log (user_seq, region, activity_type) VALUES (?, 'KR', 'LOGIN')", u1);

            List<UserListData> all = queryRepository.findDisplayableUsers(listQuery(null, null, null, 10));
            assertThat(all).extracting(UserListData::getUserSeq).containsExactly(u1, u2, u3);
            UserListData first = all.get(0);
            assertThat(first.getUserId()).isEqualTo("list-a");
            assertThat(first.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(first.getRegisterUserSeq()).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
            assertThat(first.getRegisterDate()).isNotNull();
            assertThat(first.getLastLoginDate()).as("LOGIN 로그 MAX").isNotNull();
            assertThat(all.get(1).getLastLoginDate()).isNull();

            assertThat(queryRepository.findDisplayableUsers(listQuery(UserStatus.INACTIVE, null, null, 10)))
                    .extracting(UserListData::getUserSeq).containsExactly(u2);
            assertThat(queryRepository.findDisplayableUsers(listQuery(null, "st-c", null, 10)))
                    .extracting(UserListData::getUserSeq).containsExactly(u3);

            List<UserListData> page1 = queryRepository.findDisplayableUsers(listQuery(null, null, null, 2));
            assertThat(page1).extracting(UserListData::getUserSeq).containsExactly(u1, u2);
            List<UserListData> page2 = queryRepository.findDisplayableUsers(listQuery(null, null, (long) u2, 2));
            assertThat(page2).extracting(UserListData::getUserSeq).containsExactly(u3);
        }

        @Test
        @DisplayName("findDisplayableUsers 역할 필터 — tb_user_role 보유 기준(하나라도), ADMIN+USER 계정은 양쪽, userId 필터는 관리자에도")
        void displayableByRoles() {
            int admin2 = insert("admin-x", "엑스", UserStatus.INACTIVE, RoleType.ADMIN, TENANT);
            int user = insertUser("user-x", "유저", UserStatus.ACTIVE);
            int both = insert("both-x", "둘", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'USER', ?)", both, SUPER_ADMIN);

            assertThat(queryRepository.findDisplayableUsers(listQuery(List.of(RoleType.ADMIN), null, null, null, 10)))
                    .extracting(UserListData::getUserSeq).containsExactly(ADMIN, admin2, both);
            assertThat(queryRepository.findDisplayableUsers(listQuery(List.of(RoleType.USER), null, null, null, 10)))
                    .extracting(UserListData::getUserSeq).containsExactly(user, both);
            assertThat(queryRepository.findDisplayableUsers(listQuery(List.of(RoleType.ADMIN, RoleType.USER), null, null, null, 10)))
                    .as("둘 다 — 중복 없이 한 번").extracting(UserListData::getUserSeq).containsExactly(ADMIN, admin2, user, both);
            assertThat(queryRepository.findDisplayableUsers(listQuery(List.of(RoleType.ADMIN), UserStatus.ACTIVE, "-x", null, 10)))
                    .extracting(UserListData::getUserSeq).containsExactly(both);

            List<UserListData> all = queryRepository.findDisplayableUsers(listQuery(List.of(RoleType.ADMIN, RoleType.USER), null, "-x", null, 10));
            assertThat(all).as("roles = 보유 역할 전부(enum 순서), 계정당 1행")
                    .extracting(UserListData::getUserSeq, UserListData::getRoles)
                    .containsExactly(tuple(admin2, List.of(RoleType.ADMIN)), tuple(user, List.of(RoleType.USER)), tuple(both, List.of(RoleType.ADMIN, RoleType.USER)));
        }

        @Test
        @DisplayName("searchTenantUsers 역할 조건 — tb_user_role 보유 기준(하나라도), 계정당 1행, 항목에 보유 역할 전부, 이름순 — 이름은 ASCII(컨테이너 collation 무관)")
        void searchByRoles() {
            int admin2 = insert("srch-adm", "s-a-admin", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            int user = insertUser("srch-usr", "s-b-user", UserStatus.ACTIVE);
            int both = insert("srch-both", "s-c-both", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'USER', ?)", both, SUPER_ADMIN);

            assertThat(search("srch-", List.of(RoleType.USER))).extracting(UserSearchData::getUserSeq).containsExactly(user, both);
            assertThat(search("srch-", List.of(RoleType.ADMIN))).extracting(UserSearchData::getUserSeq).containsExactly(admin2, both);
            assertThat(search("srch-", List.of(RoleType.ADMIN, RoleType.USER)))
                    .extracting(UserSearchData::getUserSeq, UserSearchData::getRoles)
                    .containsExactly(tuple(admin2, List.of(RoleType.ADMIN)), tuple(user, List.of(RoleType.USER)), tuple(both, List.of(RoleType.ADMIN, RoleType.USER)));
            assertThat(search("c-bo", List.of(RoleType.USER))).as("이름 부분 일치").extracting(UserSearchData::getUserSeq).containsExactly(both);

            jdbc.update("UPDATE tb_user SET is_protected = 'Y' WHERE user_seq = ?", admin2);
            assertThat(search("srch-", List.of(RoleType.ADMIN, RoleType.USER)))
                    .as("isProtected = tb_user.is_protected (Y → true, 기본 N → false)")
                    .extracting(UserSearchData::getUserSeq, UserSearchData::getIsProtected)
                    .containsExactly(tuple(admin2, true), tuple(user, false), tuple(both, false));
        }

        private List<UserSearchData> search(String keyword, List<RoleType> roles) {
            return queryRepository.searchTenantUsers(UserTenantSearchQuery.builder()
                    .tenantSeq(TENANT).userStatuses(List.of(UserStatus.ACTIVE)).roleTypes(roles).searchKeyword(keyword).build());
        }

        @Test
        @DisplayName("계정 목록 jOOQ — 기준 SQL(EXISTS tb_user_role)과 역할·조건 조합별 결과(행·순서·전 컬럼) 동일")
        void displayableListsMatchLegacySql() {
            int a = insertUser("pq-a", "가", UserStatus.ACTIVE);
            int b = insertUser("pq_b", "나", UserStatus.INACTIVE);
            insertUser("pqxc", "다", UserStatus.ACTIVE);
            withdrawUser(insertUser("pq-d", "라", UserStatus.ACTIVE), Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            insert("pq-adm", "관", UserStatus.INACTIVE, RoleType.ADMIN, TENANT);
            int both = insert("pq-both", "둘", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'USER', ?)", both, SUPER_ADMIN);
            insert("pq-none", "무", UserStatus.ACTIVE, RoleType.USER, null); // 테넌트 밖
            jdbc.update("INSERT INTO tb_user_log (user_seq, region, activity_type, register_date) VALUES (?, 'KR', 'LOGIN', ?)", a, java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            jdbc.update("INSERT INTO tb_user_log (user_seq, region, activity_type, register_date) VALUES (?, 'KR', 'LOGIN', ?)", a, java.sql.Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")));
            jdbc.update("INSERT INTO tb_user_log (user_seq, region, activity_type, register_date) VALUES (?, 'KR', 'LOGOUT', ?)", a, java.sql.Timestamp.from(Instant.parse("2026-03-01T00:00:00Z")));
            jdbc.update("INSERT INTO tb_user_log (user_seq, region, activity_type, register_date) VALUES (?, 'KR', 'LOGIN', ?)", ADMIN, java.sql.Timestamp.from(Instant.parse("2026-01-15T00:00:00Z")));

            int compared = 0;
            for (List<RoleType> roles : List.of(List.of(RoleType.USER), List.of(RoleType.ADMIN), List.of(RoleType.ADMIN, RoleType.USER))) {
                for (UserStatus status : java.util.Arrays.asList(null, UserStatus.ACTIVE, UserStatus.INACTIVE)) {
                    for (String userId : java.util.Arrays.asList(null, "", "pq-", "pq_", "%", "nothing")) {
                        for (Long cursorId : java.util.Arrays.asList(null, 0L, (long) a, (long) b, 3_000_000_000L)) {
                            for (int pageSize : List.of(1, 2, 50)) {
                                UserListQuery q = listQuery(roles, status, userId, cursorId, pageSize);
                                List<UserListData> actual = queryRepository.findDisplayableUsers(q);
                                assertThat(actual)
                                        .as("roles=%s status=%s userId=%s cursor=%s size=%s", roles, status, userId, cursorId, pageSize)
                                        .usingRecursiveFieldByFieldElementComparator()
                                        .containsExactlyElementsOf(legacyList(q));
                                compared++;
                            }
                        }
                    }
                }
            }
            assertThat(compared).isEqualTo(3 * 3 * 6 * 5 * 3);
            assertThat(queryRepository.findDisplayableUsers(listQuery(null, null, null, 50)))
                    .as("LOGIN 만의 MAX — LOGOUT 무시")
                    .filteredOn(d -> d.getUserSeq() == a).singleElement()
                    .extracting(UserListData::getLastLoginDate).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        }

        /** 비교 기준 — 전환 전 구현(JdbcClient, cd5404e6d)의 SQL 에서 역할 조건만 tb_user_role EXISTS 로, userId 필터는 역할 무관(⑦-1 통합), roles 는 tb_user_role 직접 조회(⑦-2) */
        private List<UserListData> legacyList(UserListQuery query) {
            var page = query.getPage();
            var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("roles", query.getRoles().stream().map(Enum::name).toList())
                    .addValue("tenantSeq", query.getTenantSeq(), java.sql.Types.INTEGER)
                    .addValue("pageSize", page.getPageSize());
            StringBuilder where = new StringBuilder("WHERE EXISTS (SELECT 1 FROM tb_user_role r WHERE r.user_seq = u.user_seq AND r.role_type IN (:roles))\n")
                    .append("AND u.tenant_seq = :tenantSeq\n");
            if (query.getStatus() != null) {
                where.append("AND u.status = :status\n");
                params.addValue("status", query.getStatus().name(), java.sql.Types.VARCHAR);
            } else {
                where.append("AND u.status IN ('ACTIVE', 'INACTIVE')\n");
            }
            if (page.getCursorId() != null) {
                where.append("AND u.user_seq > :cursorId\n");
                params.addValue("cursorId", page.getCursorId());
            }
            if (query.getUserId() != null && !query.getUserId().isEmpty()) {
                where.append("AND u.user_id LIKE CONCAT('%', :userId, '%')\n");
                params.addValue("userId", query.getUserId());
            }
            String select = """
                    SELECT u.user_seq, u.user_id, u.status, u.user_name, u.total_quota_byte, u.use_quota_byte,
                           u.register_user_seq, u.register_date, u.update_user_seq, u.update_date, l.last_login_date, u.is_protected
                    FROM tb_user u
                    LEFT JOIN (
                        SELECT user_seq, MAX(register_date) AS last_login_date
                        FROM tb_user_log
                        WHERE activity_type = 'LOGIN'
                        GROUP BY user_seq
                    ) l ON l.user_seq = u.user_seq
                    """;
            return jdbcClient.sql(select + where + "ORDER BY u.user_seq ASC\nFETCH FIRST :pageSize ROWS ONLY\n")
                    .paramSource(params)
                    .query((rs, rowNum) -> UserListData.builder()
                            .userSeq(rs.getObject("user_seq", Integer.class))
                            .userId(rs.getString("user_id"))
                            .userName(rs.getString("user_name"))
                            .status(UserStatus.valueOf(rs.getString("status")))
                            .totalQuotaByte(rs.getObject("total_quota_byte", Long.class))
                            .useQuotaByte(rs.getObject("use_quota_byte", Long.class))
                            .registerUserSeq(rs.getObject("register_user_seq", Integer.class))
                            .registerDate(instant(rs, "register_date"))
                            .updateUserSeq(rs.getObject("update_user_seq", Integer.class))
                            .updateDate(instant(rs, "update_date"))
                            .lastLoginDate(instant(rs, "last_login_date"))
                            .roles(heldRoles(rs.getObject("user_seq", Integer.class)))
                            .isProtected("Y".equals(rs.getString("is_protected")))
                            .build())
                    .list();
        }

        /** 비교 기준 보유 역할 — ARRAY 서브쿼리와 별개로 tb_user_role 을 직접 읽어 enum 순서로 */
        private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
            java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        }

        private List<RoleType> heldRoles(Integer userSeq) {
            return jdbc.queryForList("SELECT role_type FROM tb_user_role WHERE user_seq = ?", String.class, userSeq).stream()
                    .map(RoleType::valueOf).sorted().toList();
        }

        @Test
        @DisplayName("findVisibleUsers·findUserTenantsByUserIds·searchTenantUsers·findUserStatusByUserIds — IN 목록 + 빈 목록 가드")
        void inListQueries() {
            int u1 = insertUser("in-a", "가", UserStatus.ACTIVE);
            int u2 = insertUser("in-b", "나", UserStatus.INACTIVE);

            List<UserData> visible = queryRepository.findVisibleUsers(UserVisibleQuery.builder().userSeqList(List.of(SUPER_ADMIN, ADMIN, u1)).build());
            assertThat(visible).extracting(UserData::getUserSeq).containsExactlyInAnyOrder(SUPER_ADMIN, ADMIN, u1);
            assertThat(visible.stream().filter(d -> d.getUserSeq() == ADMIN).findFirst().orElseThrow().getTenantName()).isEqualTo("기본 테넌트");
            assertThat(visible.stream().filter(d -> d.getUserSeq() == SUPER_ADMIN).findFirst().orElseThrow().getTenantName()).isNull();
            assertThat(queryRepository.findVisibleUsers(UserVisibleQuery.builder().userSeqList(List.of()).build())).isEmpty();

            List<UserData> tenants = queryRepository.findUserTenantsByUserIds(List.of("admin", "superadmin"));
            assertThat(tenants).extracting(UserData::getUserId).containsExactlyInAnyOrder("admin", "superadmin");
            assertThat(tenants.stream().filter(d -> d.getUserId().equals("admin")).findFirst().orElseThrow().getTenantSeq()).isEqualTo(TENANT);
            insertUser("in-del", "삭제", UserStatus.DELETED);
            assertThat(queryRepository.findUserTenantsByUserIds(List.of("in-del", "in-a"))).as("삭제된 계정 제외")
                    .extracting(UserData::getUserId).containsExactly("in-a");

            List<UserSearchData> searched = queryRepository.searchTenantUsers(UserTenantSearchQuery.builder()
                    .tenantSeq(TENANT).userStatuses(List.of(UserStatus.ACTIVE, UserStatus.INACTIVE)).roleTypes(List.of(RoleType.USER, RoleType.ADMIN)).searchKeyword("in-").build());
            assertThat(searched).extracting(UserSearchData::getUserSeq).containsExactly(u1, u2);
            assertThat(searched.get(0).getRoles()).containsExactly(RoleType.USER);
            assertThat(queryRepository.searchTenantUsers(UserTenantSearchQuery.builder()
                    .tenantSeq(TENANT).userStatuses(List.of()).roleTypes(List.of(RoleType.USER)).searchKeyword("").build())).isEmpty();

            List<UserBulkStatusData> statuses = queryRepository.findUserStatusByUserIds(UserBulkStatusQuery.builder()
                    .tenantSeq(TENANT).userIds(List.of("in-a", "in-b", "admin", "nope")).build());
            assertThat(statuses).extracting(UserBulkStatusData::getUserId).containsExactlyInAnyOrder("in-a", "in-b");
            assertThat(statuses.stream().filter(d -> d.getUserId().equals("in-b")).findFirst().orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertThat(queryRepository.findUserStatusByUserIds(UserBulkStatusQuery.builder().tenantSeq(TENANT).userIds(List.of()).build())).isEmpty();
        }

        @Test
        @DisplayName("findShareCandidates — tenant 한정/전체, 키워드, user_name 정렬 · findOwnerCandidates — 나 자신 포함")
        void candidates() {
            int u1 = insertUser("cand-a", "다", UserStatus.ACTIVE);
            int u2 = insertUser("cand-b", "가", UserStatus.ACTIVE);
            insertUser("cand-c", "나", UserStatus.INACTIVE);

            List<UserData> inTenant = queryRepository.findShareCandidates(UserSearchQuery.builder()
                    .searchKeyword("cand").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).tenantSeq(TENANT).build());
            assertThat(inTenant).extracting(UserData::getUserSeq).as("user_name 정렬").containsExactly(u2, u1);
            assertThat(inTenant.get(0).getTenantName()).isEqualTo("기본 테넌트");

            List<UserData> anyTenant = queryRepository.findShareCandidates(UserSearchQuery.builder()
                    .searchKeyword("cand").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE, UserStatus.INACTIVE)).build());
            assertThat(anyTenant).hasSize(3);

            List<UserData> owners = queryRepository.findOwnerCandidates(UserSearchQuery.builder()
                    .searchKeyword("").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).requestUserSeq(u1).build());
            assertThat(owners).extracting(UserData::getUserSeq).as("공유 관계 없으면 나 자신만").containsExactly(u1);
            assertThat(queryRepository.findOwnerCandidates(UserSearchQuery.builder()
                    .searchKeyword("").roleType(RoleType.USER).userStatuses(List.of()).requestUserSeq(u1).build())).isEmpty();
        }

        @Test
        @DisplayName("findShareCandidates·findOwnerCandidates·findUserStatusByUserIds — USER 보유 기준(tb_user_role): USER+ADMIN 포함, ADMIN 만 제외")
        void candidatesAndBulkStatusByHeldRole() {
            int user = insertUser("hr-user", "hr-a", UserStatus.ACTIVE);
            int both = insert("hr-both", "hr-b", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'USER', ?)", both, SUPER_ADMIN);
            int adminOnly = insert("hr-adm", "hr-c", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);

            assertThat(queryRepository.findShareCandidates(UserSearchQuery.builder()
                    .searchKeyword("hr-").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).tenantSeq(TENANT).build()))
                    .extracting(UserData::getUserSeq).containsExactly(user, both);

            assertThat(queryRepository.findOwnerCandidates(UserSearchQuery.builder()
                    .searchKeyword("").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).requestUserSeq(both).build()))
                    .extracting(UserData::getUserSeq).as("USER+ADMIN 은 나 자신 포함").containsExactly(both);
            assertThat(queryRepository.findOwnerCandidates(UserSearchQuery.builder()
                    .searchKeyword("").roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).requestUserSeq(adminOnly).build()))
                    .as("ADMIN 만 — USER 미보유라 제외").isEmpty();

            assertThat(queryRepository.findUserStatusByUserIds(UserBulkStatusQuery.builder()
                    .tenantSeq(TENANT).userIds(List.of("hr-user", "hr-both", "hr-adm")).build()))
                    .extracting(UserBulkStatusData::getUserSeq).containsExactlyInAnyOrder(user, both);
        }

        @Test
        @DisplayName("findOwnerCandidates — share_target 양방향(나에게 공유한 사람·내가 공유한 사람) + 나 · 키워드 null 이면 전체 일치(공유 후보도)")
        void ownerCandidatesByShareTargetAndNullKeyword() {
            int me = insertUser("own-me", "own-a", UserStatus.ACTIVE);
            int sharedToMe = insertUser("own-from", "own-b", UserStatus.ACTIVE);
            int iSharedTo = insertUser("own-to", "own-c", UserStatus.ACTIVE);
            insertUser("own-none", "own-d", UserStatus.ACTIVE);
            String share = """
                    INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, register_user_seq)
                    VALUES (?, ?, ?, ?, now(), ?)""";
            jdbc.update(share, me, 9001L, 9001L, sharedToMe, SUPER_ADMIN);     // sharedToMe → me
            jdbc.update(share, iSharedTo, 9002L, 9002L, me, SUPER_ADMIN);      // me → iSharedTo

            assertThat(queryRepository.findOwnerCandidates(UserSearchQuery.builder()
                    .searchKeyword(null).roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).requestUserSeq(me).build()))
                    .extracting(UserData::getUserSeq).as("이름순, 관계 없는 own-none 제외").containsExactly(me, sharedToMe, iSharedTo);
            assertThat(queryRepository.findShareCandidates(UserSearchQuery.builder()
                    .searchKeyword(null).roleType(RoleType.USER).userStatuses(List.of(UserStatus.ACTIVE)).tenantSeq(TENANT).build()))
                    .extracting(UserData::getUserId).as("키워드 null = 전체 일치").contains("own-me", "own-from", "own-to", "own-none");
        }

        @Test
        @DisplayName("findPendingDeleteTargets roleType — ADMIN 보유 여부(tb_user_role)")
        void pendingDeleteTargetRoleFromHeldRoles() {
            Instant base = Instant.parse("2026-01-10T00:00:00Z");
            int userWithAdmin = insertUser("pdr-a", "가", UserStatus.ACTIVE);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'ADMIN', ?)", userWithAdmin, SUPER_ADMIN);
            int userOnly = insert("pdr-b", "나", UserStatus.ACTIVE, RoleType.ADMIN, TENANT);
            jdbc.update("DELETE FROM tb_user_role WHERE user_seq = ? AND role_type = 'ADMIN'", userOnly);
            jdbc.update("INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) VALUES (?, 'USER', ?)", userOnly, SUPER_ADMIN);
            withdrawUser(userWithAdmin, base, base.plus(30, ChronoUnit.DAYS));
            withdrawUser(userOnly, base, base.plus(30, ChronoUnit.DAYS));

            assertThat(queryRepository.findPendingDeleteTargets(UserPendingDeleteTargetQuery.builder().userSeqList(List.of(userWithAdmin, userOnly)).build()))
                    .extracting(UserPendingDeleteData::getUserSeq, UserPendingDeleteData::getRoleType)
                    .containsExactlyInAnyOrder(tuple(userWithAdmin, RoleType.ADMIN), tuple(userOnly, RoleType.USER));
        }

        @Test
        @DisplayName("findPendingDeleteUsers — 삭제자 조인·기간 필터·(pending_delete_date, user_seq) 커서 · findPendingDeleteTargets — IN·due 필터")
        void pendingDelete() {
            Instant base = Instant.parse("2026-01-10T00:00:00Z");
            int p1 = insertUser("pd-a", "가", UserStatus.ACTIVE);
            int p2 = insertUser("pd-b", "나", UserStatus.ACTIVE);
            int p3 = insertUser("pd-c", "다", UserStatus.ACTIVE);
            withdraw(p1, base, base.plus(30, ChronoUnit.DAYS));
            withdraw(p2, base.plus(1, ChronoUnit.DAYS), base.plus(31, ChronoUnit.DAYS));
            withdraw(p3, base.plus(1, ChronoUnit.DAYS), base.plus(60, ChronoUnit.DAYS));

            List<UserPendingDeleteData> all = queryRepository.findPendingDeleteUsers(pendingQuery(null, null, null, null, null, null, 10));
            assertThat(all).extracting(UserPendingDeleteData::getUserSeq).as("pending DESC, seq DESC").containsExactly(p3, p2, p1);
            UserPendingDeleteData top = all.get(0);
            assertThat(top.getStatus()).isEqualTo(UserStatus.PENDING_DELETE);
            assertThat(top.getPendingDeleteDate()).isEqualTo(base.plus(1, ChronoUnit.DAYS));
            assertThat(top.getDeleteScheduleTime()).isEqualTo(base.plus(60, ChronoUnit.DAYS));
            assertThat(top.getDeleteUserSeq()).isEqualTo(ADMIN);
            assertThat(top.getDeleteUserId()).isEqualTo("admin");
            assertThat(top.getDeleteUserStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(top.getRoles()).as("보유 역할(tb_user_role)").containsExactly(RoleType.USER);

            assertThat(queryRepository.findPendingDeleteUsers(pendingQuery(base, base.plus(1, ChronoUnit.DAYS), null, null, null, null, 10)))
                    .extracting(UserPendingDeleteData::getUserSeq).as("pending [base, base+1d)").containsExactly(p1);
            assertThat(queryRepository.findPendingDeleteUsers(pendingQuery(null, null, base.plus(31, ChronoUnit.DAYS), base.plus(61, ChronoUnit.DAYS), null, null, 10)))
                    .extracting(UserPendingDeleteData::getUserSeq).containsExactly(p3, p2);

            List<UserPendingDeleteData> page1 = queryRepository.findPendingDeleteUsers(pendingQuery(null, null, null, null, null, null, 2));
            assertThat(page1).extracting(UserPendingDeleteData::getUserSeq).containsExactly(p3, p2);
            UserPendingDeleteData last = page1.get(1);
            List<UserPendingDeleteData> page2 = queryRepository.findPendingDeleteUsers(
                    pendingQuery(null, null, null, null, (long) last.getUserSeq(), last.getPendingDeleteDate().toString(), 2));
            assertThat(page2).extracting(UserPendingDeleteData::getUserSeq).containsExactly(p1);

            List<UserPendingDeleteData> targets = queryRepository.findPendingDeleteTargets(UserPendingDeleteTargetQuery.builder().build());
            assertThat(targets).extracting(UserPendingDeleteData::getUserSeq).containsExactlyInAnyOrder(p1, p2, p3);
            assertThat(targets.get(0).getTenantSeq()).isEqualTo(TENANT);
            assertThat(queryRepository.findPendingDeleteTargets(UserPendingDeleteTargetQuery.builder().userSeqList(List.of(p1, ADMIN)).build()))
                    .extracting(UserPendingDeleteData::getUserSeq).containsExactly(p1);
            assertThat(queryRepository.findPendingDeleteTargets(UserPendingDeleteTargetQuery.builder().deleteDueAt(base.plus(45, ChronoUnit.DAYS)).build()))
                    .extracting(UserPendingDeleteData::getUserSeq).as("schedule < due").containsExactlyInAnyOrder(p1, p2);
        }

        private void withdraw(int userSeq, Instant pendingDeleteDate, Instant scheduleTime) {
            withdrawUser(userSeq, pendingDeleteDate, scheduleTime);
        }

        private UserPendingDeleteListQuery pendingQuery(Instant pStart, Instant pEnd, Instant sStart, Instant sEnd, Long cursorId, String cursorValue, int pageSize) {
            return UserPendingDeleteListQuery.builder().tenantSeq(TENANT)
                    .pendingDeleteStartDate(pStart).pendingDeleteEndDate(pEnd).deleteScheduleStartDate(sStart).deleteScheduleEndDate(sEnd)
                    .page(page(cursorId, cursorValue, pageSize)).build();
        }
    }

    // ================================================================== 쓰기

    @Nested
    @DisplayName("INSERT·FOR UPDATE")
    class InsertAndLock {

        @Test
        @DisplayName("insertUserWithSeatCheck — identity 키 주입, register_user_seq 비면 컨텍스트 사용자, DB 기본값(mfa·landing)")
        void insertDefaults() {
            User u = User.createUser("ins-a", TENANT, Password.of("{bcrypt}x"), SignupType.SSO_OIDC, "삽입", UserStatus.ACTIVE, 1024L, null, "img", "KR", "010");
            assertThat(repository.insertUserWithSeatCheck(u, false, 0)).isTrue();
            assertThat(u.getUserSeq()).isPositive();
            assertThat(col(u.getUserSeq(), "register_user_seq", Integer.class)).as("JdbcAudit → 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(col(u.getUserSeq(), "mfa_status", String.class)).isEqualTo("DISABLED");
            assertThat(col(u.getUserSeq(), "signup_type", String.class)).isEqualTo("SSO_OIDC");
            assertThat(col(u.getUserSeq(), "enc_pwd", String.class)).isEqualTo("{bcrypt}x");

            User reloaded = loadEntity(u.getUserSeq());
            assertThat(reloaded.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.HOME);
            assertThat(reloaded.getUserLanguage()).isEqualTo(UserLanguage.AUTO);
            assertThat(reloaded.getCountryCode()).isEqualTo("KR");
        }

        @Test
        @DisplayName("insertAll — 전부 삽입 count, 시트 초과 시 INSERT_FAIL")
        void insertAll() {
            List<User> two = List.of(
                    User.createUser("all-a", TENANT, null, SignupType.LOCAL, "가", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null),
                    User.createUser("all-b", TENANT, null, SignupType.LOCAL, "나", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null));
            assertThat(repository.insertAll(two, false, 0)).isEqualTo(2);
            assertThat(two).allSatisfy(u -> assertThat(u.getUserSeq()).isPositive());

            List<User> blocked = List.of(User.createUser("all-c", TENANT, null, SignupType.LOCAL, "다", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null));
            assertThatThrownBy(() -> repository.insertAll(blocked, true, 1)).isInstanceOf(ApiRuntimeException.class);
        }

        @Test
        @DisplayName("같은 userId — 다른 테넌트에는 저장되고 테넌트별로 조회, 같은 테넌트 유니크 위반은 DUPLICATE_EMAIL(500 아님)")
        void sameUserIdPerTenant() {
            int mine = insertUser("dup-x", "가", UserStatus.ACTIVE);
            int other = insert("dup-x", "나", UserStatus.ACTIVE, RoleType.USER, TENANT + 1);
            assertThat(queryRepository.findActiveAuthByUserId(TENANT, "dup-x", RoleType.USER).getUserSeq()).isEqualTo(mine);
            assertThat(queryRepository.findActiveAuthByUserId(TENANT + 1, "dup-x", RoleType.USER).getUserSeq()).isEqualTo(other);

            // 유니크 위반은 맨 끝(PG 트랜잭션 abort)
            User dup = User.createUser("dup-x", TENANT, null, SignupType.LOCAL, "다", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
            assertThatThrownBy(() -> repository.insertUserWithSeatCheck(dup, false, 0))
                    .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
        }

        @Test
        @DisplayName("테넌트 없는 계정(슈퍼관리자)끼리 같은 userId 는 유니크 위반 — COALESCE 인덱스, DUPLICATE_EMAIL")
        void sameUserIdWithoutTenant() {
            User dup = User.createUser("superadmin", null, null, SignupType.LOCAL, "중복", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
            assertThatThrownBy(() -> repository.insertUserWithSeatCheck(dup, false, 0))
                    .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
        }

        @Test
        @DisplayName("lockUser·lockUsers — @Lock JPQL 관리 엔티티(전체 컬럼, 첫 로드라 @Modifying 으로 바뀐 DB 값), 판정만 하면 UPDATE 없음, 없으면 null/빈 목록")
        void locks() {
            int u = insertUser("lock-a", "잠금", UserStatus.ACTIVE);
            repository.addUseQuotaByte(u, 77L);
            jdbc.update("UPDATE tb_user SET update_date = NULL WHERE user_seq = ?", u);

            User locked = repository.lockUser(u);
            assertThat(entityManager.contains(locked)).as("관리 엔티티").isTrue();
            assertThat(locked.getUserSeq()).isEqualTo(u);
            assertThat(locked.getUseQuotaByte()).as("이 트랜잭션의 첫 로드 — DB 값").isEqualTo(77L);
            assertThat(locked.getTotalQuotaByte()).isZero();
            assertThat(locked.getTenantSeq()).isEqualTo(TENANT);
            assertThat(locked.getUserId()).as("전체 컬럼(구 부분 엔티티 아님)").isEqualTo("lock-a");
            assertThat(repository.lockUser(99999)).isNull();

            repository.addUseQuotaByte(u, 3L); // 잠근 행을 원자적 증감(QuotaService 흐름) — 관리 엔티티는 고치지 않았으니 flush 가 덮어쓰지 않는다
            assertThat(col(u, "use_quota_byte", Long.class)).isEqualTo(80L);
            assertThat(col(u, "update_date", Object.class)).as("판정만 한 잠금 엔티티 — UPDATE 없음").isNull();

            List<User> list = repository.lockUsers(List.of(ADMIN, u, 99999));
            assertThat(list).extracting(User::getUserSeq).containsExactlyInAnyOrder(ADMIN, u);
            assertThat(list).allMatch(entityManager::contains);
            assertThat(repository.lockUsers(List.of())).isEmpty();
        }

        @Test
        @DisplayName("findById — 쓰기 리포 고칠 행 로드는 관리 엔티티, 없으면 null")
        void findById() {
            assertThat(entityManager.contains(repository.findById(ADMIN))).isTrue();
            assertThat(repository.findById(99999)).isNull();
        }
    }

    @Nested
    @DisplayName("UPDATE")
    class Updates {

        @Test
        @DisplayName("quota 원자적 증감·delete_date 읽기(Instant 왕복)·login failure 원자 증가/초기화(ACTIVE 만) — 유지 예외 JPQL")
        void simpleUpdates() {
            int u = insertUser("up-a", "수정", UserStatus.ACTIVE);
            assertThat(repository.addUseQuotaByte(u, 100L)).isEqualTo(1);
            assertThat(repository.addUseQuotaByte(u, -30L)).isEqualTo(1);
            assertThat(col(u, "use_quota_byte", Long.class)).isEqualTo(70L);
            assertThat(repository.addUseQuotaByte(99999, 1L)).as("없는 사용자 0행").isZero();

            Instant deleteDate = DriveTime.toInstant(LocalDateTime.of(2030, 1, 2, 3, 4, 5));
            jdbc.update("UPDATE tb_user SET delete_date = ? WHERE user_seq = ?", java.sql.Timestamp.from(deleteDate), u);
            Instant storedDeleteDate = jdbc.queryForObject("SELECT delete_date FROM tb_user WHERE user_seq = ?", (rs, i) -> rs.getTimestamp(1).toInstant(), u);
            assertThat(storedDeleteDate).isEqualTo(deleteDate);
            assertThat(loadEntity(u).getDeleteDate()).as("JPA 컨버터도 같은 벽시계").isEqualTo(deleteDate);

            repository.increaseLoginFailureCount(u);
            repository.increaseLoginFailureCount(u);
            assertThat(col(u, "login_failure_count", Integer.class)).isEqualTo(2);
            repository.resetLoginFailureCount(u);
            assertThat(col(u, "login_failure_count", Integer.class)).isZero();
            int inactive = insertUser("up-b", "비활성", UserStatus.INACTIVE);
            repository.increaseLoginFailureCount(inactive);
            assertThat(col(inactive, "login_failure_count", Integer.class)).as("ACTIVE 만 증가").isZero();
        }

        @Test
        @DisplayName("변경 감지 — 프로필 키·비밀번호(+실패 횟수 초기화는 ACTIVE 만), 같은 값이면 UPDATE 없음(Password equals), 감사는 시큐리티 사용자·현재 시각")
        void profileAndPassword() {
            int u = insertUser("pw-a", "비번", UserStatus.ACTIVE);
            jdbc.update("UPDATE tb_user SET login_failure_count = 3, enc_pwd = '{bcrypt}old', update_date = NULL WHERE user_seq = ?", u);
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            User user = repository.findById(u);
            user.changeProfileImageKey("new-key");
            assertThat(col(u, "profile_image_key", String.class)).isEqualTo("new-key");
            user.changePassword(Password.of("{bcrypt}changed"));
            assertThat(col(u, "enc_pwd", String.class)).isEqualTo("{bcrypt}changed");
            assertThat(col(u, "login_failure_count", Integer.class)).isZero();
            assertThat(col(u, "update_user_seq", Integer.class)).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(jdbc.queryForObject("SELECT update_date FROM tb_user WHERE user_seq = ?", java.time.OffsetDateTime.class, u).toInstant()).isAfterOrEqualTo(before);

            jdbc.update("UPDATE tb_user SET update_date = NULL WHERE user_seq = ?", u);
            user.changePassword(Password.of("{bcrypt}changed")); // 같은 값(새 인스턴스) — equals 로 변경 없음
            assertThat(col(u, "update_date", Object.class)).as("Password equals → UPDATE 없음").isNull();

            int inactive = insertUser("pw-b", "비활성", UserStatus.INACTIVE);
            jdbc.update("UPDATE tb_user SET login_failure_count = 2 WHERE user_seq = ?", inactive);
            repository.findById(inactive).changePassword(Password.of("{bcrypt}x"));
            assertThat(col(inactive, "enc_pwd", String.class)).isEqualTo("{bcrypt}x");
            assertThat(col(inactive, "login_failure_count", Integer.class)).as("ACTIVE 가 아니면 실패 횟수 그대로(구 reset 조건)").isEqualTo(2);
        }

        @Test
        @DisplayName("변경 감지 — changeByAdmin·changeProfile(language/landing 문자열 값), 감사는 Auditing(update_date 갱신)")
        void adminAndSelfUpdates() {
            int u = insertUser("adm-a", "원래", UserStatus.ACTIVE);
            jdbc.update("UPDATE tb_user SET update_date = NULL WHERE user_seq = ?", u);
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            User user = repository.findById(u);
            user.changeByAdmin("관리자수정", 5000L, "111", "p", "US");
            assertThat(col(u, "user_name", String.class)).isEqualTo("관리자수정");
            assertThat(col(u, "total_quota_byte", Long.class)).isEqualTo(5000L);
            assertThat(col(u, "profile_image_key", String.class)).isEqualTo("p");
            assertThat(col(u, "update_user_seq", Integer.class)).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(jdbc.queryForObject("SELECT update_date FROM tb_user WHERE user_seq = ?", java.time.OffsetDateTime.class, u).toInstant()).isAfterOrEqualTo(before);

            jdbc.update("UPDATE tb_user SET update_date = NULL WHERE user_seq = ?", u);
            entityManager.refresh(user); // JdbcTemplate 로 바꾼 행 — 스냅샷을 DB 값으로(안 하면 Auditing 수정일이 스냅샷과 같아 UPDATE 에서 빠짐)
            user.changeProfile("222", UserLanguage.KO, DefaultLandingPage.MY_DRIVE, "q", "KR");
            assertThat(col(u, "user_language", String.class)).isEqualTo("ko");
            assertThat(col(u, "default_landing_page", String.class)).isEqualTo("my-drive");
            assertThat(col(u, "phone_number", String.class)).isEqualTo("222");
            assertThat(col(u, "update_user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(col(u, "update_date", Object.class)).as("W7 — 본인 수정도 update_date 갱신(Auditing)").isNotNull();
            User reloaded = loadEntity(u);
            assertThat(reloaded.getUserLanguage()).isEqualTo(UserLanguage.KO);
            assertThat(reloaded.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.MY_DRIVE);
        }

        @Test
        @DisplayName("updateAll 배치 UPDATE(B1) — 행마다 값, profile 미변경, 감사 = 시큐리티 사용자·현재 시각, 같은 값이어도 update_date 갱신, 빈 목록 0, 실패는 UPDATE_FAIL")
        void updateAll_batch() {
            int u = insertUser("bat-a", "하나", UserStatus.ACTIVE);
            int v = insertUser("bat-b", "둘", UserStatus.ACTIVE);
            jdbc.update("UPDATE tb_user SET profile_image_key = 'q', update_user_seq = NULL, update_date = NULL WHERE user_seq IN (?, ?)", u, v);
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            int count = repository.updateAll(List.of(
                    new AdminUserBulkUpdateQuery(u, "배치1", 1L, "KR", "1"),
                    new AdminUserBulkUpdateQuery(v, "배치2", 2L, "US", "2")));
            assertThat(count).isEqualTo(2);
            assertThat(col(u, "user_name", String.class)).isEqualTo("배치1");
            assertThat(col(v, "user_name", String.class)).isEqualTo("배치2");
            assertThat(col(v, "total_quota_byte", Long.class)).isEqualTo(2L);
            assertThat(col(v, "country_code", String.class)).isEqualTo("US");
            assertThat(col(v, "phone_number", String.class)).isEqualTo("2");
            assertThat(col(u, "profile_image_key", String.class)).as("일괄 수정은 profile 미변경").isEqualTo("q");
            assertThat(col(u, "update_user_seq", Integer.class)).as("JdbcAudit — 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(jdbc.queryForObject("SELECT update_date FROM tb_user WHERE user_seq = ?", java.time.OffsetDateTime.class, v).toInstant()).isAfterOrEqualTo(before);

            jdbc.update("UPDATE tb_user SET update_user_seq = NULL, update_date = NULL WHERE user_seq = ?", u);
            assertThat(repository.updateAll(List.of(new AdminUserBulkUpdateQuery(u, "배치1", 1L, "KR", "1")))).isEqualTo(1);
            assertThat(col(u, "update_date", Object.class)).as("같은 값이어도 UPDATE — update_date 갱신(구 배치 동작)").isNotNull();
            assertThat(col(u, "update_user_seq", Integer.class)).isEqualTo(ADMIN);

            assertThat(repository.updateAll(List.of())).isZero();
            assertThat(repository.updateAll(null)).isZero();

            // 맨 끝 — PG 는 오류 뒤 트랜잭션이 abort 된다. user_name NOT NULL 위반 → UPDATE_FAIL 로 감쌈
            assertThatThrownBy(() -> repository.updateAll(List.of(new AdminUserBulkUpdateQuery(u, null, 1L, "KR", "1"))))
                    .isInstanceOf(ApiRuntimeException.class)
                    .extracting(e -> ((ApiRuntimeException) e).getCode())
                    .isEqualTo(UserErrorCode.UPDATE_FAIL);
        }

        @Test
        @DisplayName("updateAll 섞임 — 같은 tx 에서 lockUsers 로 잠근 관리 엔티티(고치지 않음)는 flush 때 배치 결과를 덮어쓰지 않는다, 메모리 값은 배치 전 값")
        void updateAll_afterLockUsers_notOverwrittenByFlush() {
            int u = insertUser("bat-l", "잠금전", UserStatus.ACTIVE);
            User locked = repository.lockUsers(List.of(u)).get(0);

            repository.updateAll(List.of(new AdminUserBulkUpdateQuery(u, "배치후", 9L, "US", "9")));
            entityManager.flush();

            assertThat(col(u, "user_name", String.class)).isEqualTo("배치후");
            assertThat(col(u, "total_quota_byte", Long.class)).isEqualTo(9L);
            assertThat(locked.getUserName()).as("관리 엔티티는 배치 전 값(읽지 말 것)").isEqualTo("잠금전");
        }

        @Test
        @DisplayName("updateAll 실측 — 20행이 prepareStatement 1·addBatch 20·executeBatch 1(JDBC 배치 1회), 행마다 executeUpdate 없음")
        void updateAll_isSingleJdbcBatch() {
            List<AdminUserBulkUpdateQuery> queries = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int seq = insertUser("bat-n" + i, "n" + i, UserStatus.ACTIVE);
                queries.add(new AdminUserBulkUpdateQuery(seq, "새" + i, (long) i, "KR", String.valueOf(i)));
            }
            JdbcStatementCounter counter = new JdbcStatementCounter(dataSource);
            UserRepository counted = new DefaultUserRepository(userJpaRepository, DSL.using(counter, SQLDialect.POSTGRES));

            assertThat(counted.updateAll(queries)).isEqualTo(20);

            assertThat(counter.prepareStatementCount()).isEqualTo(1);
            assertThat(counter.addBatchCount()).isEqualTo(20);
            assertThat(counter.executeBatchCount()).isEqualTo(1);
            assertThat(counter.executeUpdateCount()).isZero();
            assertThat(col(queries.get(19).userSeq(), "user_name", String.class)).isEqualTo("새19");
        }

        @Test
        @DisplayName("updateAll 분할 — CHUNK_SIZE+1 행은 executeBatch 2회(1000+1), 처리 건수 = 항목 수, 마지막 행까지 반영")
        void updateAll_splitsIntoChunks() {
            int rows = JooqBatches.CHUNK_SIZE + 1;
            List<AdminUserBulkUpdateQuery> queries = new java.util.ArrayList<>();
            for (int i = 0; i < rows; i++) {
                int seq = insertUser("bat-c" + i, "c" + i, UserStatus.ACTIVE);
                queries.add(new AdminUserBulkUpdateQuery(seq, "분할" + i, (long) i, "KR", String.valueOf(i)));
            }
            JdbcStatementCounter counter = new JdbcStatementCounter(dataSource);
            UserRepository counted = new DefaultUserRepository(userJpaRepository, DSL.using(counter, SQLDialect.POSTGRES));

            assertThat(counted.updateAll(queries)).isEqualTo(rows);

            assertThat(counter.addBatchCount()).isEqualTo(rows);
            assertThat(counter.executeBatchCount()).isEqualTo(2);
            assertThat(col(queries.get(rows - 1).userSeq(), "user_name", String.class)).isEqualTo("분할" + (rows - 1));
        }

        @Test
        @DisplayName("activateUser — ACTIVE 아닌 행만 ACTIVE·수정 감사, SEAT 면 테넌트 ACTIVE 수 < constrainValue 일 때만(0건 = 시트 초과)")
        void activateUser_seatCheck() {
            int u = insertUser("act-a", "활성화", UserStatus.INACTIVE);
            int activeCount = jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE status = 'ACTIVE' AND tenant_seq = ?", Integer.class, TENANT);

            assertThat(repository.activateUser(SUPER_ADMIN, u, TENANT, true, activeCount)).as("시트 꽉 참").isZero();
            assertThat(col(u, "status", String.class)).isEqualTo("INACTIVE");

            assertThat(repository.activateUser(SUPER_ADMIN, u, TENANT, true, activeCount + 1)).isEqualTo(1);
            assertThat(col(u, "status", String.class)).isEqualTo("ACTIVE");
            assertThat(col(u, "update_user_seq", Integer.class)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
            assertThat(repository.activateUser(SUPER_ADMIN, u, TENANT, false, 0)).as("이미 ACTIVE").isZero();

            int v = insertUser("act-b", "비시트", UserStatus.INACTIVE);
            assertThat(repository.activateUser(SUPER_ADMIN, v, TENANT, false, 0)).as("비-SEAT 는 시트 무관").isEqualTo(1);
            assertThat(repository.activateUser(SUPER_ADMIN, 999_999, TENANT, false, 0)).as("없음").isZero();
        }

        @Test
        @DisplayName("상태 전이 변경 감지 — deactivate(같은 상태면 false)·withdraw(ACTIVE/INACTIVE 만)·restore(PENDING_DELETE 만), markUsersAsDeleted 벌크(requirePendingDelete 분기·빈 목록)")
        void statusTransitions() {
            int u = insertUser("st-a", "상태", UserStatus.ACTIVE);
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            User user = repository.findById(u);
            assertThat(user.deactivate()).isTrue();
            assertThat(col(u, "status", String.class)).isEqualTo("INACTIVE");
            assertThat(user.deactivate()).as("이미 INACTIVE").isFalse();

            Instant pending = Instant.parse("2026-02-01T00:00:00Z");
            assertThat(user.withdraw(pending, pending.plus(30, ChronoUnit.DAYS))).isTrue();
            assertThat(col(u, "status", String.class)).isEqualTo("PENDING_DELETE");
            assertThat(col(u, "update_user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(user.withdraw(pending, pending)).as("ACTIVE/INACTIVE 만").isFalse();
            assertThat(repository.markUsersAsDeleted(UserDeleteMarkQuery.builder().userSeqList(List.of(ADMIN)).deleteDate(now).requirePendingDelete(true).build())).as("ACTIVE 는 제외").isZero();

            assertThat(user.restore()).isTrue();
            assertThat(col(u, "status", String.class)).isEqualTo("INACTIVE");
            assertThat(col(u, "pending_delete_date", Object.class)).isNull();
            assertThat(user.restore()).isFalse();

            assertThat(repository.markUsersAsDeleted(UserDeleteMarkQuery.builder().userSeqList(List.of(u)).deleteDate(now).requirePendingDelete(true).build())).as("INACTIVE 는 PENDING 요구 시 제외").isZero();
            user.changeProfileImageKey("gone");
            entityManager.flush();
            assertThat(repository.markUsersAsDeleted(UserDeleteMarkQuery.builder().userSeqList(List.of(u)).deleteDate(now).updateUserSeq(SUPER_ADMIN).requirePendingDelete(false).build())).isEqualTo(1);
            assertThat(col(u, "status", String.class)).isEqualTo("DELETED");
            assertThat(col(u, "profile_image_key", String.class)).as("벌크 JPQL 뒤 관리 엔티티는 옛 값이지만 dirty 가 아니라 덮어쓰지 않음").isNull();
            assertThat(col(u, "total_quota_byte", Long.class)).isZero();
            assertThat(col(u, "update_user_seq", Integer.class)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
            assertThat(jdbc.queryForObject("SELECT delete_date FROM tb_user WHERE user_seq = ?", java.time.OffsetDateTime.class, u).toInstant()).isEqualTo(now);
            assertThat(repository.markUsersAsDeleted(UserDeleteMarkQuery.builder().userSeqList(List.of()).deleteDate(now).build())).isZero();
            assertThat(queryRepository.existsUserId(TENANT, "st-a")).as("DELETED 는 중복 검사에서 제외").isFalse();
        }

        @Test
        @DisplayName("MFA — setup(DISABLED/PENDING)·confirm(PENDING + setup >= cutoff)·cancel(ENABLED + tenant)·clearPending(setup < cutoff, 전체)")
        void mfa() {
            int u = insertUser("mfa-a", "엠에프에이", UserStatus.ACTIVE);
            Instant setup = Instant.parse("2026-03-01T00:00:00Z");
            assertThat(repository.setupMfaTotp(UserMfaQuery.builder().userSeq(u).mfaSecretKey("secret").mfaSetupDate(setup).build())).isEqualTo(1);
            assertThat(col(u, "mfa_status", String.class)).isEqualTo("PENDING");
            assertThat(col(u, "mfa_method", String.class)).isEqualTo("TOTP");
            assertThat(col(u, "mfa_secret_key", String.class)).isEqualTo("secret");

            assertThat(repository.confirmMfaTotp(UserMfaQuery.builder().userSeq(u).mfaConfirmDate(setup.plusSeconds(60)).cutoffAt(setup.plusSeconds(1)).build())).as("setup < cutoff 면 만료").isZero();
            assertThat(repository.confirmMfaTotp(UserMfaQuery.builder().userSeq(u).mfaConfirmDate(setup.plusSeconds(60)).cutoffAt(setup.minusSeconds(1)).build())).isEqualTo(1);
            assertThat(col(u, "mfa_status", String.class)).isEqualTo("ENABLED");
            User enabled = loadEntity(u);
            assertThat(enabled.getMfaStatus()).isEqualTo(MfaStatus.ENABLED);
            assertThat(enabled.getMfaMethod()).isEqualTo(MfaMethod.TOTP);
            assertThat(enabled.getMfaConfirmDate()).isEqualTo(setup.plusSeconds(60));

            // 해제는 변경 감지(W7) — 위 JPQL 로 바뀐 행을 이 트랜잭션에서 처음 관리 로드(loadEntity 는 detach)
            User managed = repository.findById(u);
            assertThat(managed.cancelMfa(999)).as("다른 테넌트").isFalse();
            assertThat(managed.cancelMfa(TENANT)).isTrue();
            assertThat(managed.cancelMfa(TENANT)).as("이미 DISABLED").isFalse();
            assertThat(col(u, "mfa_status", String.class)).isEqualTo("DISABLED");
            assertThat(col(u, "mfa_method", String.class)).isNull();
            assertThat(col(u, "mfa_secret_key", String.class)).isNull();

            assertThat(repository.setupMfaTotp(UserMfaQuery.builder().userSeq(u).mfaSecretKey("s2").mfaSetupDate(setup).build())).isEqualTo(1);
            assertThat(repository.clearPendingMfaTotp(UserMfaQuery.builder().cutoffAt(setup).build())).as("setup < cutoff 아님").isZero();
            assertThat(repository.clearPendingMfaTotp(UserMfaQuery.builder().cutoffAt(setup.plusSeconds(1)).build())).isEqualTo(1);
            assertThat(col(u, "mfa_status", String.class)).isEqualTo("DISABLED");
            assertThat(col(u, "mfa_setup_date", Object.class)).isNull();
        }
    }
}
