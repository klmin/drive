package com.thinkfree.drive.user.role;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.license.service.LicenseService.SeatPolicy;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.sso.protocol.oidc.dto.OidcSignupDto;
import com.thinkfree.drive.sso.protocol.oidc.service.OidcSignupService;
import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.result.AdminUserInsertResult;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.repository.UserRoleQueryRepository;
import com.thinkfree.drive.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 계정을 만드는 저장 지점 3곳(단건 {@code UserService.registerUser} — 단건 등록·테넌트 생성, 일괄 {@code AdminUserService.registerAll/upsertAll} — CSV·bulk,
 * {@code OidcSignupService.signup})이 같은 트랜잭션에서 tb_user_role 에 요청 역할 1개를 부여하는지(PostgreSQL Testcontainers, 트랜잭션 롤백).
 * 스토리지(Ceph) 가용량은 이 테스트 밖이라 {@link QuotaService} 를 목으로 통과시킨다. 시큐리티 사용자 없음 → 부여자 = tb_user 등록자.
 */
@DisplayName("역할 부여 공통 메서드 — 계정 생성 경로별 tb_user_role 부여")
class UserRoleAssignmentIntegrationTest extends RepositoryTest {

    private static final int TENANT = 1;
    private static final int ADMIN = 2;
    private static final long GB = 1024L * 1024 * 1024;
    private static final String PW = "PgUr!pass1";

    @MockitoBean
    private QuotaService quotaService;

    @Autowired
    private UserService userService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private OidcSignupService oidcSignupService;
    @Autowired
    private UserRoleQueryRepository userRoleQueryRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private static AdminUserSaveDto saveDto(String userId, RoleType role, long quota) {
        return new AdminUserSaveDto(userId, TENANT, PW, userId, quota, ADMIN, Set.of(role), null, null);
    }

    private Integer seqOf(String userId) {
        return jdbc.queryForObject("SELECT user_seq FROM tb_user WHERE user_id = ?", Integer.class, userId);
    }

    private Integer roleRegisterUserSeq(int userSeq) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT register_user_seq FROM tb_user_role WHERE user_seq = ?", Integer.class, userSeq);
    }

    @Test
    @DisplayName("단건 등록(UserService.registerUser) — USER·ADMIN 각 1개, 부여자 = tb_user 등록자")
    void registerUser() {
        int user = userService.registerUser(saveDto("ura-user@test.com", RoleType.USER, GB), null).userSeq();
        int admin = userService.registerUser(saveDto("ura-admin@test.com", RoleType.ADMIN, 0L), null).userSeq();

        assertThat(userRoleQueryRepository.findRoles(user)).containsExactly(RoleType.USER);
        assertThat(userRoleQueryRepository.findRoles(admin)).containsExactly(RoleType.ADMIN);
        assertThat(roleRegisterUserSeq(user)).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("일괄(registerAll — CSV, upsertAll — bulk) — 계정마다 요청 역할 1개, 수정만 있으면 부여 없음")
    void registerAllAndUpsertAll() {
        List<AdminUserInsertResult> csv = adminUserService.registerAll(
                List.of(saveDto("ura-csv1@test.com", RoleType.USER, GB), saveDto("ura-csv2@test.com", RoleType.USER, GB)), new SeatPolicy(false, 0));
        assertThat(csv).allSatisfy(r -> assertThat(userRoleQueryRepository.findRoles(r.getUserSeq())).containsExactly(RoleType.USER));

        List<AdminUserInsertResult> bulk = adminUserService.upsertAll(
                List.of(saveDto("ura-bulk-u@test.com", RoleType.USER, GB), saveDto("ura-bulk-a@test.com", RoleType.ADMIN, 0L)), List.of(), new SeatPolicy(false, 0));
        assertThat(userRoleQueryRepository.findRoles(seqOf("ura-bulk-u@test.com"))).containsExactly(RoleType.USER);
        assertThat(userRoleQueryRepository.findRoles(seqOf("ura-bulk-a@test.com"))).containsExactly(RoleType.ADMIN);
        assertThat(bulk).hasSize(2);

        int target = csv.get(0).getUserSeq();
        adminUserService.upsertAll(List.of(), List.of(new AdminUserBulkUpdateDto(target, "renamed", GB, null, null)), new SeatPolicy(false, 0));
        assertThat(userRoleQueryRepository.findRoles(target)).as("수정은 역할을 건드리지 않음").containsExactly(RoleType.USER);
    }

    @Test
    @DisplayName("OIDC 가입(OidcSignupService.signup) — USER 1개")
    void oidcSignup() {
        var result = oidcSignupService.signup(new OidcSignupDto("ura-oidc@test.com", "oidc", TENANT, 1, "ura-sub-1", "ura-oidc@test.com", "oidc"), new MockHttpServletRequest());

        assertThat(result.accessToken()).isNotBlank();
        assertThat(userRoleQueryRepository.findRoles(result.userSeq())).containsExactly(RoleType.USER);
    }

    @Test
    @DisplayName("부여 불가 역할(SUPER_ADMIN)로 들어온 계정 — 공통 메서드가 USER_013 으로 거부(호출부 검사를 지나친 경우의 마지막 방어)")
    void exclusiveRoleRejected() {
        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> userService.registerUser(saveDto("ura-sa@test.com", RoleType.SUPER_ADMIN, 0L), null));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.INSERT_FAIL);
    }
}
