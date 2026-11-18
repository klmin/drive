package com.thinkfree.drive.application.admin.user;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.admin.user.converter.AdminUserCommandConverter;
import com.thinkfree.drive.application.admin.user.converter.AdminUserInfoConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.tenant.TenantValidator;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.metrics.user.service.UserMetricsService;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.userlog.service.UserLogFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AdminUserUseCase가 <b>UserAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * 기록 내용은 {@code DefaultUserAuditServiceTest}에서 검증한다.
 */
@DisplayName("AdminUserUseCase 감사 로그 기록 흐름 테스트")
class AdminUserUseCaseAuditTest extends AuditMockTest {

    private static final int TENANT = 1;

    @Mock private AdminUserCommandConverter adminUserCommandConverter;
    @Mock private AdminUserInfoConverter adminUserInfoConverter;
    @Mock private UserService userService;
    @Mock private TenantPolicy tenantPolicy;
    @Mock private UserFindService userFindService;
    @Mock private UserMetricsService userMetricsService;
    @Mock private MfaService mfaService;
    @Mock private com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService userPermanentDeletionService;
    @Mock private LicenseService licenseService;
    @Mock private SeatLicenseService seatLicenseService;
    @Mock private QuotaService quotaService;
    @Mock private TenantUserQuotaGuard tenantUserQuotaGuard;
    @Mock private AdminUserService adminUserService;
    @Mock private UserLogFindService userLogFindService;
    @Mock private TenantValidator tenantValidator;
    @Mock private UserAuditLookupService userAuditLookupService;
    @Mock private UserAuditService userAuditService;

    @InjectMocks
    private AdminUserUseCase adminUserUseCase;

    // EventName: USER_TWOFACTOR_RESET
    @Test
    @DisplayName("resetMfa - lookup으로 조회한 유저로 record(USER_TWOFACTOR_RESET)를 호출한다")
    void resetMfa_recordsTwoFactorReset() {
        TenantContext.set(TENANT);
        UserDetailData user = Mockito.mock(UserDetailData.class);
        given(user.getUserSeq()).willReturn(20);
        given(user.getUserId()).willReturn("user20@thinkfree.com");
        given(user.getUserName()).willReturn("사용자20");
        given(userAuditLookupService.findUser(anyInt())).willReturn(user);

        adminUserUseCase.resetMfa(20);

        verify(userAuditService).record(EventName.USER_TWOFACTOR_RESET, user);
    }
}
