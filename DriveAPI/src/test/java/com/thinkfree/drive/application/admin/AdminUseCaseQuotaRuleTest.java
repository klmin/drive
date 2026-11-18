package com.thinkfree.drive.application.admin;

import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import com.thinkfree.drive.application.admin.converter.AdminCommandConverter;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.user.dto.UserSaveResult;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 단건 등록·관리자 수정의 보유 역할별 할당량·역할 교체 규칙 — USER 보유면 용량 필수·0 보다 큼, ADMIN 만이면 0(생략 시 0).
 * 외부 채널(API Key·OAuth)도 ADMIN 등록 가능(채널 제한 없음 — registerUser 가 채널을 받지 않는다).
 * 회수 규칙(본인 ADMIN·사용량 있는 USER)은 UserRoleService 몫 — 여기서는 잠근 사용량으로 위임하는지만 본다(DefaultUserRoleServiceTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserUseCase — 단건 등록·수정 역할별 할당량·역할 교체")
class AdminUseCaseQuotaRuleTest {

    private static final int TENANT = 1;
    private static final int LOGIN_USER = 1;
    private static final Set<RoleType> USER_ONLY = Set.of(RoleType.USER);
    private static final Set<RoleType> ADMIN_ONLY = Set.of(RoleType.ADMIN);
    private static final Set<RoleType> USER_ADMIN = Set.of(RoleType.USER, RoleType.ADMIN);

    @Mock private AdminCommandConverter adminCommandConverter;
    @Mock private UserService userService;
    @Mock private TenantPolicy tenantPolicy;
    @Mock private UserFindService userFindService;
    @Mock private SeatLicenseService seatLicenseService;
    @Mock private QuotaService quotaService;
    @Mock private TenantUserQuotaGuard tenantUserQuotaGuard;
    @Mock private UserAuditLookupService userAuditLookupService;
    @Mock private UserAuditService userAuditService;
    @Mock private UserRoleService userRoleService;
    @Mock private UserRoleFindService userRoleFindService;

    @InjectMocks
    private AdminUserUseCase adminUserUseCase;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(userService.registerUser(any(), any())).willReturn(new UserSaveResult(100));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static AdminRegisterUserCommand register(Set<RoleType> roles, Long tq) {
        return new AdminRegisterUserCommand("new@thinkfree.com", "Passw0rd!x", "name", tq, roles, null, null, null);
    }

    private static AdminUpdateUserCommand update(Long tq) {
        return update(tq, null);
    }

    private static AdminUpdateUserCommand update(Long tq, Set<RoleType> roles) {
        return AdminUpdateUserCommand.builder().userName("name").totalQuotaByte(tq).roles(roles).build();
    }

    private User lockedUser(Set<RoleType> roles, long totalQuotaByte) {
        return lockedUser(roles, totalQuotaByte, 0L);
    }

    private User lockedUser(Set<RoleType> roles, long totalQuotaByte, long useQuotaByte) {
        // 잠금 전(스토리지 판정용) 조회와 잠금 조회가 같은 값을 돌려주는 경우
        UserDetailData current = Mockito.mock(UserDetailData.class);
        given(current.getTotalQuotaByte()).willReturn(totalQuotaByte);
        given(current.getUseQuotaByte()).willReturn(useQuotaByte);
        given(userFindService.findDetailByUserSeq(anyInt())).willReturn(current);
        given(userRoleFindService.findRoles(anyInt())).willReturn(roles);

        User user = Mockito.mock(User.class);
        given(user.getTotalQuotaByte()).willReturn(totalQuotaByte);
        given(user.getUseQuotaByte()).willReturn(useQuotaByte);
        given(user.isInvalidTotalQuotaChange(anyLong())).willReturn(false);
        given(userService.lockUserBySeq(anyInt())).willReturn(user);
        return user;
    }

    private AdminUpdateUserCommand savedUpdate() {
        ArgumentCaptor<AdminUpdateUserCommand> saved = ArgumentCaptor.forClass(AdminUpdateUserCommand.class);
        then(adminCommandConverter).should().toUpdateDto(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("ADMIN 등록 — 용량 생략은 0 으로 저장되고 테넌트 총 용량 증감 0")
    void register_adminNullQuota_savedAsZero() {
        adminUserUseCase.registerUser(register(ADMIN_ONLY, null), LOGIN_USER);

        ArgumentCaptor<AdminRegisterUserCommand> saved = ArgumentCaptor.forClass(AdminRegisterUserCommand.class);
        then(adminCommandConverter).should().toSaveDto(saved.capture(), eq(LOGIN_USER), eq(TENANT));
        assertThat(saved.getValue().totalQuotaByte()).isZero();
        then(tenantUserQuotaGuard).should().ensureAssignable(TENANT, 0L);
    }

    @Test
    @DisplayName("ADMIN 등록에 양수 용량이면 QUOTA_009, USER 등록에 용량 누락이면 REQUIRED_FIELD·0 이면 QUOTA_008 — 저장 없음")
    void register_quotaByRole_rejected() {
        var admin = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.registerUser(register(ADMIN_ONLY, 1L), LOGIN_USER));
        assertThat(admin.getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);

        var user = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.registerUser(register(USER_ONLY, null), LOGIN_USER));
        assertThat(user.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        var zero = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.registerUser(register(USER_ONLY, 0L), LOGIN_USER));
        assertThat(zero.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);

        then(userService).should(never()).registerUser(any(), any());
    }

    @Test
    @DisplayName("USER 등록 — 용량만큼 테넌트 총 용량 판정")
    void register_user_checksTenantQuota() {
        adminUserUseCase.registerUser(register(USER_ONLY, 5L), LOGIN_USER);

        then(tenantUserQuotaGuard).should().ensureAssignable(TENANT, 5L);
    }

    @Test
    @DisplayName("USER·ADMIN 동시 등록 — USER 규칙(용량 필수), 대표 역할은 ADMIN")
    void register_userAndAdmin() {
        var missing = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.registerUser(register(USER_ADMIN, null), LOGIN_USER));
        assertThat(missing.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        adminUserUseCase.registerUser(register(USER_ADMIN, 5L), LOGIN_USER);

        ArgumentCaptor<AdminRegisterUserCommand> saved = ArgumentCaptor.forClass(AdminRegisterUserCommand.class);
        then(adminCommandConverter).should().toSaveDto(saved.capture(), eq(LOGIN_USER), eq(TENANT));
        assertThat(saved.getValue().roles()).containsExactlyInAnyOrder(RoleType.USER, RoleType.ADMIN);
        then(tenantUserQuotaGuard).should().ensureAssignable(TENANT, 5L);
    }

    @Test
    @DisplayName("등록 — 역할 없음·빈 목록·SUPER_ADMIN·ANONYMOUS 포함은 USER_028, 저장 없음")
    void register_invalidRoles_rejected() {
        for (Set<RoleType> roles : java.util.Arrays.asList(null, Set.<RoleType>of(), Set.of(RoleType.SUPER_ADMIN),
                Set.of(RoleType.ANONYMOUS), Set.of(RoleType.USER, RoleType.SUPER_ADMIN))) {
            var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.registerUser(register(roles, 1L), LOGIN_USER));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        }
        then(userService).should(never()).registerUser(any(), any());
    }

    @Test
    @DisplayName("관리자 수정 — 대상이 ADMIN 만 보유면 생략은 0, 양수는 QUOTA_009")
    void update_adminTarget() {
        lockedUser(ADMIN_ONLY, 0L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 10, update(null));
        assertThat(savedUpdate().totalQuotaByte()).isZero();

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 10, update(1L)));
        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("관리자 수정 — USER·ADMIN 보유 대상은 USER 규칙(tb_user.role_type 이 ADMIN 이어도 용량 0 강제 안 함)")
    void update_userAndAdminTarget_userQuotaRule() {
        lockedUser(USER_ADMIN, 10L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(15L));

        assertThat(savedUpdate().totalQuotaByte()).isEqualTo(15L);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, 5L);
    }

    @Test
    @DisplayName("관리자 수정 — 대상이 SUPER_ADMIN·ANONYMOUS(설치 시 계정)·역할 없음이면 USER_NOT_FOUND, 저장 없음")
    void update_unregistrableTarget_rejected() {
        for (Set<RoleType> roles : java.util.List.of(Set.of(RoleType.SUPER_ADMIN), Set.of(RoleType.ANONYMOUS), Set.<RoleType>of())) {
            lockedUser(roles, 0L);
            var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 30, update(0L)));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
        then(userService).should(never()).updateUserByAdmin(anyInt(), anyInt(), any());
        then(userRoleService).should(never()).replaceRoles(any());
    }

    @Test
    @DisplayName("관리자 수정 — roles 생략은 역할 유지(교체 없음, 대표 역할 null)")
    void update_rolesOmitted_keepsRoles() {
        lockedUser(USER_ONLY, 10L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(10L));

        then(userRoleService).should(never()).replaceRoles(any());
        savedUpdate();
    }

    @Test
    @DisplayName("관리자 수정 — USER 에 ADMIN 추가: 잠근 사용량으로 교체 위임, 대표 역할 ADMIN, 용량은 USER 규칙")
    void update_grantAdmin() {
        lockedUser(USER_ONLY, 10L, 3L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(10L, USER_ADMIN));

        then(userRoleService).should().replaceRoles(new UserRoleChangeDto(20, USER_ADMIN, LOGIN_USER, 3L));
        assertThat(savedUpdate().totalQuotaByte()).isEqualTo(10L);
    }

    @Test
    @DisplayName("관리자 수정 — ADMIN 만 보유에 USER 추가: 용량 필수(생략 REQUIRED_FIELD), 지정하면 증가분으로 테넌트 총 용량 판정")
    void update_grantUser() {
        lockedUser(ADMIN_ONLY, 0L);

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(null, USER_ADMIN)));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(7L, USER_ADMIN));
        assertThat(savedUpdate().totalQuotaByte()).isEqualTo(7L);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, 7L);
    }

    @Test
    @DisplayName("관리자 수정 — USER·ADMIN 에서 USER 회수: 용량 생략이면 0 으로 확정, 대표 역할 ADMIN")
    void update_revokeUser_quotaZero() {
        lockedUser(USER_ADMIN, 10L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(null, ADMIN_ONLY));

        then(userRoleService).should().replaceRoles(new UserRoleChangeDto(20, ADMIN_ONLY, LOGIN_USER, 0L));
        assertThat(savedUpdate().totalQuotaByte()).isZero();
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, -10L);
    }

    @Test
    @DisplayName("관리자 수정 — 교체 규칙 위반(USER_030 등)은 용량 판정·저장 전에 거부")
    void update_replaceRejected_beforeQuota() {
        lockedUser(USER_ADMIN, 10L, 5L);
        Mockito.doThrow(ApiRuntimeException.type(UserErrorCode.ROLE_REVOKE_USER_IN_USE))
               .when(userRoleService).replaceRoles(any());

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(null, ADMIN_ONLY)));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.ROLE_REVOKE_USER_IN_USE);
        then(tenantUserQuotaGuard).should(never()).ensureAssignableLocked(anyInt(), anyLong());
        then(userService).should(never()).updateUserByAdmin(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("관리자 수정 — roles 가 빈 목록·SUPER_ADMIN 포함이면 USER_028, 조회·잠금 전에 거부")
    void update_invalidRoles_rejected() {
        lockedUser(USER_ONLY, 10L);
        for (Set<RoleType> roles : java.util.List.of(Set.<RoleType>of(), Set.of(RoleType.SUPER_ADMIN), Set.of(RoleType.USER, RoleType.ANONYMOUS))) {
            var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(10L, roles)));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        }
        then(userService).should(never()).lockUserBySeq(anyInt());
        then(userRoleService).should(never()).replaceRoles(any());
    }

    @Test
    @DisplayName("관리자 수정 — 스토리지(Ceph) 가용량 확인은 테넌트·사용자 잠금보다 먼저(잠금을 쥔 채 외부 호출 안 함)")
    void update_storageCheckBeforeLocks() {
        lockedUser(USER_ONLY, 10L);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(15L));

        var order = Mockito.inOrder(quotaService, tenantUserQuotaGuard, userService);
        order.verify(quotaService).ensureCanUpdateQuota(15L, 10L, 0L);
        order.verify(tenantUserQuotaGuard).lockTenant(TENANT);
        order.verify(userService).lockUserBySeq(20);
        order.verify(tenantUserQuotaGuard).ensureAssignableLocked(TENANT, 5L);
    }

    @Test
    @DisplayName("관리자 수정 — 대상이 USER 면 용량 필수, 증감분(새 − 기존)으로 테넌트 총 용량 판정")
    void update_userTarget() {
        lockedUser(USER_ONLY, 10L);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(null)));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(15L));
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, 5L);
    }

    @Test
    @DisplayName("관리자 수정 — USER 를 0 으로 바꾸면 QUOTA_008, 저장 없음")
    void update_userTarget_changeToZero_rejected() {
        lockedUser(USER_ONLY, 10L);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(0L)));
        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
        then(userService).should(never()).updateUserByAdmin(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("관리자 수정 — 기존 할당량이 0 인 USER 도 0 그대로면 QUOTA_008, 저장 없음")
    void update_userTarget_keepZero_rejected() {
        lockedUser(USER_ONLY, 0L);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(0L)));
        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
        then(userService).should(never()).updateUserByAdmin(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("관리자 수정 — SYSTEM_MANAGED 보호 계정이면 TENANT_011, 조회·스토리지 확인·잠금 전에 거부")
    void update_systemManagedProtected_rejectedFirst() {
        Mockito.doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED)).when(tenantPolicy).requireUpdatableUser(20);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, update(15L)));
        assertThat(e.getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        then(userFindService).should(never()).findDetailByUserSeq(anyInt());
        then(quotaService).should(never()).ensureCanUpdateQuota(anyLong(), anyLong(), anyLong());
        then(tenantUserQuotaGuard).should(never()).lockTenant(anyInt());
        then(userService).should(never()).updateUserByAdmin(anyInt(), anyInt(), any());
    }
}
