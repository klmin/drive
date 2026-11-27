package com.thinkfree.drive.application.admin;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import com.thinkfree.drive.application.admin.converter.AdminCommandConverter;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.converter.AdminInfoConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.user.dto.UserSaveResult;
import com.thinkfree.drive.csv.service.CsvParsingService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserIdentity;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AdminUseCase가 <b>UserAuditService/LoginAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * 각 이벤트가 채우는 context 내용(diff JSON 등)은 {@code DefaultUserAuditServiceTest}에서 검증한다.
 */
@DisplayName("AdminUserUseCase 감사 로그 기록 흐름 테스트")
class AdminUseCaseAuditTest extends AuditMockTest {

    private static final int TENANT = 1;
    private static final int LOGIN_USER = 9;

    @Mock private AdminCommandConverter adminCommandConverter;
    @Mock private AdminInfoConverter adminInfoConverter;
    @Mock private UserService userService;
    @Mock private AdminUserService adminUserService;
    @Mock private FileProvider fileProvider;
    @Mock private CsvParsingService csvParsingService;
    @Mock private LicenseService licenseService;
    @Mock private SeatLicenseService seatLicenseService;
    @Mock private UserFindService userFindService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private QuotaService quotaService;
    @Mock private TenantUserQuotaGuard tenantUserQuotaGuard;
    @Mock private BrandingMetaService brandingMetaService;
    @Mock private UserAuditLookupService userAuditLookupService;
    @Mock private UserAuditService userAuditService;
    @Mock private LoginAuditService loginAuditService;
    @Mock private UserRoleService userRoleService;
    @Mock private UserRoleFindService userRoleFindService;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private AdminUserUseCase adminUserUseCase;

    private UserDetailData userMock(int seq, RoleType role) {
        return identityMock(UserDetailData.class, seq, role);
    }

    private UserAuthData authMock(int seq, RoleType role) {
        return identityMock(UserAuthData.class, seq, role);
    }

    private <T extends UserIdentity> T identityMock(Class<T> type, int seq, RoleType role) {
        T u = Mockito.mock(type);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        // ADMIN_/USER_ 분기 = 대상의 ADMIN 역할 보유(tb_user_role)
        Mockito.lenient().when(userRoleFindService.holds(seq, RoleType.ADMIN)).thenReturn(role == RoleType.ADMIN);
        return u;
    }

    // EventName: USER_CREATE, ADMIN_CREATE
    @Test
    @DisplayName("registerUser - roles(ADMIN 보유 여부)에 따라 USER_CREATE/ADMIN_CREATE로 record(scalar)를 호출한다")
    void registerUser_recordsCreateByRole() {
        TenantContext.set(TENANT);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(userService.registerUser(any(), any())).willReturn(new UserSaveResult(100));

        var command = new AdminRegisterUserCommand("new@thinkfree.com", "encpw", "새사용자", 100L, Set.of(RoleType.USER), null, null, null);
        adminUserUseCase.registerUser(command, LOGIN_USER);

        verify(userAuditService).record(EventName.USER_CREATE, 100L, "new@thinkfree.com", "새사용자");

        var adminCommand = new AdminRegisterUserCommand("admin@thinkfree.com", "encpw", "새관리자", 0L, Set.of(RoleType.ADMIN), null, null, null);
        adminUserUseCase.registerUser(adminCommand, LOGIN_USER);

        verify(userAuditService).record(EventName.ADMIN_CREATE, 100L, "admin@thinkfree.com", "새관리자");
    }

    // EventName: USER_INFO_UPDATE, ADMIN_INFO_UPDATE
    @Test
    @DisplayName("updateUserByAdmin - lookup으로 조회한 변경 전 유저 + 요청 값으로 recordInfoUpdate를 호출한다")
    void updateUserByAdmin_recordsInfoUpdate() {
        TenantContext.set(TENANT); // 테넌트 행을 사용자 행보다 먼저 잠그므로 테넌트 컨텍스트가 필요
        User locked = Mockito.mock(User.class); // 쓰기 리포 잠금 로드(엔티티)
        given(locked.isInvalidTotalQuotaChange(anyLong())).willReturn(false);
        given(userRoleFindService.findRoles(20)).willReturn(Set.of(RoleType.USER)); // 수정 대상 보유 역할 판정(USER/ADMIN 만)
        given(userService.lockUserBySeq(anyInt())).willReturn(locked);
        UserDetailData current = userMock(20, RoleType.USER); // 잠금 전 스토리지 판정용 현재 값(잠금 없이 조회)
        given(current.getTotalQuotaByte()).willReturn(100L);
        given(current.getUseQuotaByte()).willReturn(0L);
        given(userFindService.findDetailByUserSeq(anyInt())).willReturn(current);

        UserDetailData before = userMock(20, RoleType.USER); // 감사 "변경 전" 스냅샷(DTO)
        given(userAuditLookupService.findUser(anyInt())).willReturn(before);

        var command = new AdminUpdateUserCommand("새사용자20", 100L, null, null, null, null, null);

        adminUserUseCase.updateUserByAdmin(LOGIN_USER, 20, command);

        verify(userAuditService).recordInfoUpdate(before, "새사용자20", 100L, null, null);
    }

    // EventName: ACCOUNT_LOCKED_STATUS_CHANGE(LoginAuditService), USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE(UserAuditService)
    @Test
    @DisplayName("updatePasswordByAdmin - 잠긴 계정(15분 안에 실패 5회)이면 잠금 해제도 함께 기록한다")
    void updatePasswordByAdmin_lockedAccount_recordsUnlockAndPasswordChange() {
        UserAuthData user = authMock(20, RoleType.USER);
        given(user.getLoginFailureCount()).willReturn(5);
        given(user.getLoginFailedAt()).willReturn(java.time.Instant.now().minusSeconds(60));
        given(userService.updatePasswordByAdmin(anyInt(), any(), any())).willReturn(user);

        adminUserUseCase.updatePasswordByAdmin(20, "newpw", Mockito.mock(HttpServletRequest.class));

        verify(loginAuditService).recordAccountUnlocked(user);
        verify(userAuditService).record(EventName.USER_PASSWORD_CHANGE, user);
    }

    @Test
    @DisplayName("updatePasswordByAdmin - 실패 5회라도 잠금 시간이 지났으면 이미 풀린 것이라 잠금 해제를 기록하지 않는다")
    void updatePasswordByAdmin_expiredLock_recordsPasswordChangeOnly() {
        UserAuthData user = authMock(20, RoleType.USER);
        given(user.getLoginFailureCount()).willReturn(5);
        given(user.getLoginFailedAt()).willReturn(java.time.Instant.now().minus(java.time.Duration.ofMinutes(16)));
        given(userService.updatePasswordByAdmin(anyInt(), any(), any())).willReturn(user);

        adminUserUseCase.updatePasswordByAdmin(20, "newpw", Mockito.mock(HttpServletRequest.class));

        verify(loginAuditService, Mockito.never()).recordAccountUnlocked(any());
        verify(userAuditService).record(EventName.USER_PASSWORD_CHANGE, user);
    }

    // EventName: USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE만(잠금 해제 아님)
    @Test
    @DisplayName("updatePasswordByAdmin - 잠기지 않은 계정이면 잠금 해제를 기록하지 않는다")
    void updatePasswordByAdmin_unlockedAccount_recordsPasswordChangeOnly() {
        UserAuthData user = authMock(20, RoleType.USER);
        given(user.getLoginFailureCount()).willReturn(0);
        given(userService.updatePasswordByAdmin(anyInt(), any(), any())).willReturn(user);

        adminUserUseCase.updatePasswordByAdmin(20, "newpw", Mockito.mock(HttpServletRequest.class));

        verify(loginAuditService, Mockito.never()).recordAccountUnlocked(any());
        verify(userAuditService).record(EventName.USER_PASSWORD_CHANGE, user);
    }

    // EventName: USER_STATUS_CHANGE, ADMIN_STATUS_CHANGE
    @Test
    @DisplayName("activateUser/deactivateUser - lookup으로 조회한 이전 상태를 before로 record(user, before, after)를 호출한다")
    void activateDeactivate_recordsStatusChange() {
        TenantContext.set(TENANT);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(licenseService.getSeatPolicy(anyInt())).willReturn(new LicenseService.SeatPolicy(false, 0));

        UserDetailData inactive = userMock(20, RoleType.USER);
        given(inactive.getStatus()).willReturn(UserStatus.INACTIVE);
        given(userAuditLookupService.findUser(anyInt())).willReturn(inactive);

        adminUserUseCase.activateUser(LOGIN_USER, 20);
        verify(userAuditService).record(EventName.USER_STATUS_CHANGE, inactive, "INACTIVE", "ACTIVE");

        UserDetailData active = userMock(20, RoleType.USER);
        given(active.getStatus()).willReturn(UserStatus.ACTIVE);
        given(userAuditLookupService.findUser(anyInt())).willReturn(active);

        adminUserUseCase.deactivateUser(LOGIN_USER, 20);
        verify(userAuditService).record(EventName.USER_STATUS_CHANGE, active, "ACTIVE", "INACTIVE");

        // 대상이 ADMIN 계정이면 ADMIN_STATUS_CHANGE (role==ADMIN 분기)
        UserDetailData adminInactive = userMock(21, RoleType.ADMIN);
        given(adminInactive.getStatus()).willReturn(UserStatus.INACTIVE);
        given(userAuditLookupService.findUser(anyInt())).willReturn(adminInactive);

        adminUserUseCase.activateUser(LOGIN_USER, 21);
        verify(userAuditService).record(EventName.ADMIN_STATUS_CHANGE, adminInactive, "INACTIVE", "ACTIVE");
    }

    @Test
    @DisplayName("activateUser - SEAT 시트가 없으면 USER_013(INSERT_FAIL), 활성화·기록 안 함 / 있으면 시트 정책을 넘긴다")
    void activateUser_seatCheck() {
        TenantContext.set(TENANT);
        UserDetailData inactive = userMock(20, RoleType.USER);
        given(inactive.getStatus()).willReturn(UserStatus.INACTIVE);
        given(userAuditLookupService.findUser(anyInt())).willReturn(inactive);

        given(seatLicenseService.isSeatAvailable(TENANT, 1)).willReturn(false);
        assertThatThrownBy(() -> adminUserUseCase.activateUser(LOGIN_USER, 20))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UserErrorCode.INSERT_FAIL));
        verify(userService, Mockito.never()).activateUser(any(), any(), anyInt(), anyBoolean(), anyInt());
        verify(userAuditService, Mockito.never()).record(any(EventName.class), any(UserIdentity.class), anyString(), anyString());

        given(seatLicenseService.isSeatAvailable(TENANT, 1)).willReturn(true);
        given(licenseService.getSeatPolicy(TENANT)).willReturn(new LicenseService.SeatPolicy(true, 10));
        adminUserUseCase.activateUser(LOGIN_USER, 20);
        verify(userService).activateUser(LOGIN_USER, 20, TENANT, true, 10);
    }

    // EventName: USER_DELETE, ADMIN_DELETE
    @Test
    @DisplayName("withdraw - 대상 role에 따라 USER_DELETE/ADMIN_DELETE로 record(user)를 호출한다")
    void withdraw_recordsDeleteByRole() {
        var user = userMock(20, RoleType.USER);
        given(userAuditLookupService.findUser(anyInt())).willReturn(user);
        adminUserUseCase.withdraw(LOGIN_USER, 20);
        verify(userAuditService).record(EventName.USER_DELETE, user);

        var admin = userMock(21, RoleType.ADMIN);
        given(userAuditLookupService.findUser(anyInt())).willReturn(admin);
        adminUserUseCase.withdraw(LOGIN_USER, 21);
        verify(userAuditService).record(EventName.ADMIN_DELETE, admin);
    }
}
