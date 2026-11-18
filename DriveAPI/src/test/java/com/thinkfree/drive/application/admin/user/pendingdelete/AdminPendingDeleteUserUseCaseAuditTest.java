package com.thinkfree.drive.application.admin.user.pendingdelete;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.application.admin.user.pendingdelete.converter.AdminPendingDeleteUserCommandConverter;
import com.thinkfree.drive.application.admin.user.pendingdelete.converter.AdminPendingDeleteUserInfoConverter;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService;
import com.thinkfree.drive.global.security.tenant.TenantValidator;
import com.thinkfree.drive.user.admin.service.AdminUserFindService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AdminPendingDeleteUserUseCase가 <b>UserAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * 기록 내용은 {@code DefaultUserAuditServiceTest}에서 검증한다.
 */
@DisplayName("AdminPendingDeleteUserUseCase 감사 로그 기록 흐름 테스트")
class AdminPendingDeleteUserUseCaseAuditTest extends AuditMockTest {

    @Mock private AdminPendingDeleteUserCommandConverter adminPendingDeleteUserCommandConverter;
    @Mock private AdminPendingDeleteUserInfoConverter adminPendingDeleteUserInfoConverter;
    @Mock private AdminUserFindService adminUserFindService;
    @Mock private AdminUserService adminUserService;
    @Mock private UserPermanentDeletionService userPermanentDeletionService;
    @Mock private TenantValidator tenantValidator;
    @Mock private UserAuditLookupService userAuditLookupService;
    @Mock private UserAuditService userAuditService;
    @Mock private UserRoleFindService userRoleFindService;

    @InjectMocks
    private AdminPendingDeleteUserUseCase adminPendingDeleteUserUseCase;

    // EventName: USER_RESTORE, ADMIN_RESTORE
    @Test
    @DisplayName("restore - rt에 따라 USER_RESTORE/ADMIN_RESTORE로 record(user)를 호출한다")
    void restore_recordsRestoreByRole() {
        var user = userMock(20, RoleType.USER);
        given(userAuditLookupService.findUser(anyInt())).willReturn(user);
        adminPendingDeleteUserUseCase.restore(20);
        verify(userAuditService).record(EventName.USER_RESTORE, user);

        var admin = userMock(21, RoleType.ADMIN);
        given(userAuditLookupService.findUser(anyInt())).willReturn(admin);
        adminPendingDeleteUserUseCase.restore(21);
        verify(userAuditService).record(EventName.ADMIN_RESTORE, admin);
    }

    private UserDetailData userMock(int seq, RoleType role) {
        UserDetailData u = Mockito.mock(UserDetailData.class);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        // ADMIN_/USER_ 분기 = 대상의 ADMIN 역할 보유(tb_user_role)
        Mockito.lenient().when(userRoleFindService.holds(seq, RoleType.ADMIN)).thenReturn(role == RoleType.ADMIN);
        return u;
    }
}
