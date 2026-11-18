package com.thinkfree.drive.cleanup.user.service;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.cleanup.user.converter.UserCleanUpConverter;
import com.thinkfree.drive.cleanup.user.service.dto.UserCleanUpDto;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.user.admin.service.AdminUserFindService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * UserPermanentDeletionService가 <b>UserAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * 기록 내용(actor/tenantSeq override 등)은 {@code DefaultUserAuditServiceTest}에서 검증한다.
 */
@DisplayName("UserPermanentDeletionService 감사 로그 기록 흐름 테스트")
class UserPermanentDeletionServiceAuditTest extends AuditMockTest {

    private static final int SUPER_ADMIN_SEQ = 1;

    @Mock private UserCleanUpConverter userCleanUpConverter;
    @Mock private AdminUserFindService adminUserFindService;
    @Mock private AdminUserService adminUserService;
    @Mock private ResourceFindService resourceFindService;
    @Mock private UserCleanUpService userCleanUpService;
    @Mock private UserFindService userFindService;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private UserAuditService userAuditService;
    @Mock private UserRoleFindService userRoleFindService;

    @InjectMocks
    private UserPermanentDeletionService userPermanentDeletionService;

    private UserPendingDeleteData pendingTarget(int seq, int tenantSeq, RoleType role) {
        var t = Mockito.mock(UserPendingDeleteData.class);
        given(t.getUserSeq()).willReturn(seq);
        given(t.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(t.getUserName()).willReturn("사용자" + seq);
        given(t.getTenantSeq()).willReturn(tenantSeq);
        given(t.getRoleType()).willReturn(role);
        given(t.getProfileImageKey()).willReturn(null);
        return t;
    }

    // EventName: USER_PERMANENT_DELETE, ADMIN_PERMANENT_DELETE
    @Test
    @DisplayName("deletePermanentlyFromPending - 대상마다 role에 따라 record(target, actor, tenantSeq)를 호출한다")
    void deletePermanentlyFromPending_fanOutWithActorAndTenant() {
        // 대상: 20=USER(테넌트1), 21=ADMIN(테넌트2)
        var target20 = pendingTarget(20, 1, RoleType.USER);
        var target21 = pendingTarget(21, 2, RoleType.ADMIN);
        given(adminUserFindService.findPendingDeleteTargets(any())).willReturn(List.of(target20, target21));
        given(resourceFindService.findStorageKeysByUserSeqList(any())).willReturn(List.of());

        // 행위자(슈퍼어드민) 조회
        UserDetailData actor = Mockito.mock(UserDetailData.class);
        given(actor.getUserSeq()).willReturn(SUPER_ADMIN_SEQ);
        given(actor.getUserId()).willReturn("superadmin@thinkfree.com");
        given(actor.getUserName()).willReturn("슈퍼관리자");
        given(userFindService.findDetailByUserSeq(anyInt())).willReturn(actor);

        // userSeqList=null(스케줄러형) → 개수 검증 스킵, actorUserSeq=슈퍼어드민
        var dto = new UserCleanUpDto(null, null, SUPER_ADMIN_SEQ);
        userPermanentDeletionService.deletePermanentlyFromPending(dto);

        verify(userAuditService).record(EventName.USER_PERMANENT_DELETE, target20, actor, 1);
        verify(userAuditService).record(EventName.ADMIN_PERMANENT_DELETE, target21, actor, 2);
    }

    // EventName: USER_PERMANENT_DELETE, ADMIN_PERMANENT_DELETE
    @Test
    @DisplayName("deleteUserDirectly - rt에 따라 record(USER/ADMIN_PERMANENT_DELETE, user)를 호출한다")
    void deleteUserDirectly_recordsPermanentDeleteByRole() {
        UserDetailData user = Mockito.mock(UserDetailData.class);
        given(user.getUserSeq()).willReturn(20);
        given(user.getUserId()).willReturn("user20@thinkfree.com");
        given(user.getUserName()).willReturn("사용자20");
        Mockito.lenient().when(userRoleFindService.holds(20, RoleType.ADMIN)).thenReturn(false);
        given(user.getProfileImageKey()).willReturn(null);
        given(userFindService.findDetailByUserSeq(anyInt())).willReturn(user);
        given(resourceFindService.findStorageKeysByUserSeqList(any())).willReturn(List.of());

        userPermanentDeletionService.deleteUserDirectly(20, 9);

        verify(userAuditService).record(EventName.USER_PERMANENT_DELETE, user);
    }

    // EventName: ADMIN_PERMANENT_DELETE
    @Test
    @DisplayName("deleteUserDirectly - ADMIN 보유는 cascade(역할 행 삭제) 전에 읽어 ADMIN_PERMANENT_DELETE 로 기록한다")
    void deleteUserDirectly_readsRoleBeforeCascade() {
        UserDetailData user = Mockito.mock(UserDetailData.class);
        given(user.getUserSeq()).willReturn(21);
        given(user.getProfileImageKey()).willReturn(null);
        given(userFindService.findDetailByUserSeq(anyInt())).willReturn(user);
        given(userRoleFindService.holds(21, RoleType.ADMIN)).willReturn(true);
        given(resourceFindService.findStorageKeysByUserSeqList(any())).willReturn(List.of());

        userPermanentDeletionService.deleteUserDirectly(21, 9);

        InOrder inOrder = Mockito.inOrder(userRoleFindService, userCleanUpService, userAuditService);
        inOrder.verify(userRoleFindService).holds(21, RoleType.ADMIN);
        inOrder.verify(userCleanUpService).deleteUsersCascade(List.of(21));
        inOrder.verify(userAuditService).record(EventName.ADMIN_PERMANENT_DELETE, user);
    }
}
