package com.thinkfree.drive.application.admin.user.pendingdelete;

import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeletePermanentCommand;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeleteUserListCommand;
import com.thinkfree.drive.application.admin.user.pendingdelete.converter.AdminPendingDeleteUserCommandConverter;
import com.thinkfree.drive.application.admin.user.pendingdelete.converter.AdminPendingDeleteUserInfoConverter;
import com.thinkfree.drive.application.admin.user.pendingdelete.info.AdminPendingDeleteUserListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService;
import com.thinkfree.drive.cleanup.user.service.dto.UserCleanUpDto;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.tenant.TenantValidator;
import com.thinkfree.drive.user.admin.service.AdminUserFindService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진입점(내부 API·MCP) 공용 삭제 대기 사용자 usecase — 구 api.v1 admin.user.pendingdelete AdminPendingDeleteUserUseCase의 통째 이관.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 접근 제어는 어댑터 책임 — 웹은 시큐리티 URL role 게이트(/api/v1/admins/** = ADMIN),
 * MCP는 툴 클래스의 @McpToolRole(ADMIN) 어스펙트가 담당한다.
 * 테넌트 격리는 TenantContext 기반(웹·MCP 모두 TenantFilter가 Host 기준으로 주입).
 * 웹 컨트롤러의 @TenantUser(경로 userSeq 테넌트 검증)는 MCP 경로에 없으므로, userSeq를 받는 메서드를 툴에서
 * 쓸 때는 툴 파라미터에 @McpToolTenantUser를 붙여 동일 검증을 수행해야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPendingDeleteUserUseCase {

    private final AdminPendingDeleteUserCommandConverter adminPendingDeleteUserCommandConverter;
    private final AdminPendingDeleteUserInfoConverter adminPendingDeleteUserInfoConverter;
    private final AdminUserFindService adminUserFindService;
    private final AdminUserService adminUserService;
    private final UserPermanentDeletionService userPermanentDeletionService;
    private final TenantValidator tenantValidator;
    private final UserAuditLookupService userAuditLookupService;
    private final UserAuditService userAuditService;
    private final UserRoleFindService userRoleFindService;

    /**
     * 삭제 대기 사용자 목록 — 커서는 pendingDeleteDate DESC(값) + userSeq DESC(tie-breaker).
     * 커서 값은 구 CursorApiPage 계약대로 LocalDateTime으로 내보낸다(CursorPageInfo 가 Instant 를 설정 시간대 벽시계로 바꾼다).
     */
    public CursorPageInfo<AdminPendingDeleteUserListInfo> findAllCursorPaginated(AdminPendingDeleteUserListCommand command) {
        Integer tenantSeq = TenantContext.getOrThrow();
        var query = adminPendingDeleteUserCommandConverter.toListQuery(command, tenantSeq);
        var list = adminUserFindService.findPendingDeleteUsers(query);
        return CursorPageInfo.of(adminPendingDeleteUserInfoConverter.toListInfos(list), list, command.pageSize(),
                UserPendingDeleteData::getPendingDeleteDate, UserPendingDeleteData::getUserSeq, null);
    }

    /**
     * 삭제 대기(PENDING_DELETE) 계정 복원 — INACTIVE로 되돌리고 삭제 예약 시각을 지운다.
     * 삭제 대기 상태가 아니면 도메인이 ADMIN_USER_001로 거부한다.
     */
    @AuditLog
    @TenantFeatureRequired(TenantFeature.SUB_ACCOUNT_MANAGE)
    public void restore(int userSeq) {
        var user = userAuditLookupService.findUser(userSeq);

        adminUserService.restoreUser(userSeq);

        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_RESTORE : EventName.USER_RESTORE;
        userAuditService.record(eventName, user);
    }

    @AuditLog
    @TenantFeatureRequired(TenantFeature.SUB_ACCOUNT_MANAGE)
    public void permanentDeleteUsers(AdminPendingDeletePermanentCommand command, int loginUserSeq) {
        tenantValidator.assertSameTenant(command.userSeqList());
        var userCleanUpDto = new UserCleanUpDto(command.userSeqList(), null, loginUserSeq);
        userPermanentDeletionService.deletePermanentlyFromPending(userCleanUpDto);
    }

}
