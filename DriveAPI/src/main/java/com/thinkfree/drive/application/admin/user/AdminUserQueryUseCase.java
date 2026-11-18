package com.thinkfree.drive.application.admin.user;

import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.application.admin.converter.AdminInfoConverter;
import com.thinkfree.drive.application.admin.info.AdminUserDetailInfo;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkStatusCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserListCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserLogListCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserSearchCommand;
import com.thinkfree.drive.application.admin.user.converter.AdminUserCommandConverter;
import com.thinkfree.drive.application.admin.user.converter.AdminUserInfoConverter;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkStatusInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserListInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserLogInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserSearchInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserStorageUsageInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.metrics.user.service.UserMetricsService;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.userlog.service.UserLogFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 진입점(내부 API·MCP) 공용 관리자 계정 조회 usecase — 계정 목록(사용자·관리자 통합), 단건, 검색, 로그, 사용량, 상태 일괄 조회.
 * 변경은 {@link AdminUserUseCase}, 일괄 등록/수정·CSV 가져오기는 {@link AdminUserImportUseCase}.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 접근 제어는 어댑터 책임 — 웹은 시큐리티 URL role 게이트(/api/v1/admins/** = ADMIN),
 * MCP는 툴 클래스의 @McpToolRole(ADMIN) 어스펙트가 담당한다.
 * 테넌트 격리는 TenantContext 기반(웹·MCP 모두 TenantFilter가 Host 기준으로 주입) — 조회는 항상 요청 테넌트 스코프.
 * 웹 컨트롤러의 @TenantUser(경로 userSeq 테넌트 검증)는 MCP 경로에 없으므로, userSeq를 받는 메서드를 툴에서
 * 쓸 때는 툴이 동일 검증을 명시 호출해야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserQueryUseCase {

    /** 목록·검색 대상 role — SUPER_ADMIN·ANONYMOUS 는 테넌트 관리자가 다룰 대상이 아니다. 목록 rt 미지정 기본값 */
    private static final List<RoleType> SEARCHABLE_ROLES = List.of(RoleType.ADMIN, RoleType.USER);

    /** rt 미지정 기본값 — API Key 발급 대상 선택 기준 */
    private static final List<RoleType> DEFAULT_SEARCH_ROLES = List.of(RoleType.USER);

    /** st 미지정 기본값 */
    private static final List<UserStatus> DEFAULT_SEARCH_STATUSES = List.of(UserStatus.ACTIVE);

    private final AdminInfoConverter adminInfoConverter;
    private final AdminUserCommandConverter adminUserCommandConverter;
    private final AdminUserInfoConverter adminUserInfoConverter;
    private final UserFindService userFindService;
    private final UserMetricsService userMetricsService;
    private final UserLogFindService userLogFindService;

    /**
     * 계정 목록(사용자·관리자 통합) — 역할 필터는 보유 기준(하나라도 보유), 생략 시 USER·ADMIN 전부.
     * 항목의 보유 역할은 목록 쿼리가 함께 내린다(ARRAY 서브쿼리).
     * 커서는 userSeq 단일 기준(오름차순)이라 nextCursorValue/nextCursorType은 항상 null이다 (웹 계약 동일).
     */
    public CursorPageInfo<AdminUserListInfo> findDisplayableUsersPaginated(AdminUserListCommand command) {

        validateDisplayableStatus(command.status());
        var roles = resolveListRoles(command.roles());

        Integer tenantSeq = TenantContext.getOrThrow();
        var query = adminUserCommandConverter.toListQuery(command, roles, tenantSeq);
        var list = userFindService.findDisplayableUsers(query);
        return CursorPageInfo.of(adminUserInfoConverter.toListInfos(list), list, command.pageSize(),
                null, UserListData::getUserSeq, null);
    }

    /**
     * 사용자 단건 조회 — 구조상 컨트롤러 → UserService.getUser(api.v1 응답 반환) 직결이던 것을 흡수.
     * 존재하지 않는 userSeq는 USER_NOT_FOUND. 계정 값과 보유 역할(tb_user_role, enum 순서)을 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public AdminUserDetailInfo getUser(Integer userSeq) {
        var user = userFindService.findDetailWithRoles(userSeq);
        return adminInfoConverter.toDetailInfo(user, user.getRoles());
    }

    public AdminUserStorageUsageInfo storageUsage(int userSeq){
        var result = userMetricsService.getStorageUsageMetrics(userSeq);
        return adminUserInfoConverter.toStorageUsageInfo(result);
    }

    @TenantFeatureRequired(TenantFeature.USER_REGISTER)
    public List<AdminUserBulkStatusInfo> findUserStatusByUserIds(AdminUserBulkStatusCommand command) {
        Integer tenantSeq = TenantContext.getOrThrow();
        var query = adminUserCommandConverter.toBulkStatusQuery(command, tenantSeq);
        var data = userFindService.findUserStatusByUserIds(query);
        return adminUserInfoConverter.toBulkStatusInfos(data);
    }

    /** 사용자 Log 목록 — 커서는 userLogSeq 단일 기준이라 nextCursorValue/nextCursorType은 항상 null (웹 계약 동일) */
    public CursorPageInfo<AdminUserLogInfo> findUserLogs(Integer userSeq, AdminUserLogListCommand command){
        // 도메인 서비스가 웹 페이징 요청 타입을 그대로 받는 기존 계약이라 여기서 재조립한다
        var pagingRequest = new CursorPagingRequest(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize());
        var response = userLogFindService.findMyLogs(userSeq, pagingRequest);
        var page = response.getPage();
        return new CursorPageInfo<>(adminUserInfoConverter.toLogInfos(response.getList()),
                page.getPageSize(), page.getNextCursorValue(), page.getNextCursor(), page.getNextCursorType(), page.isHasNext());
    }

    /**
     * 테넌트 사용자 검색.
     * role·status를 주면 그 범위로(role 은 하나라도 보유), 안 주면 API Key 발급 대상 기준(USER·ACTIVE)으로 내린다. 항목에 보유 역할 전부.
     */
    public List<AdminUserSearchInfo> search(AdminUserSearchCommand command) {
        Integer tenantSeq = TenantContext.getOrThrow();
        var roleTypes = resolveSearchRoleTypes(command.roleTypes());
        var userStatuses = resolveSearchUserStatuses(command.userStatuses());
        var users = userFindService.searchTenantUsers(command.searchKeyword(), tenantSeq, roleTypes, userStatuses);
        return adminUserInfoConverter.toSearchInfos(users);
    }

    private void validateDisplayableStatus(UserStatus status) {
        if (status != null && !UserStatus.displayableStatuses().contains(status)) {
            throw new ApiRuntimeException(UserErrorCode.INVALID_STATUS_PARAM);
        }
    }

    private List<RoleType> resolveListRoles(List<RoleType> roles) {
        if (roles == null || roles.isEmpty()) {
            return SEARCHABLE_ROLES;
        }
        if (!SEARCHABLE_ROLES.containsAll(roles)) {
            throw new ApiRuntimeException(UserErrorCode.INVALID_ROLE_PARAM);
        }
        return roles;
    }

    private List<RoleType> resolveSearchRoleTypes(List<RoleType> roleTypes) {
        if (roleTypes == null || roleTypes.isEmpty()) {
            return DEFAULT_SEARCH_ROLES;
        }
        if (!SEARCHABLE_ROLES.containsAll(roleTypes)) {
            throw new ApiRuntimeException(UserErrorCode.INVALID_ROLE_PARAM);
        }
        return roleTypes;
    }

    private List<UserStatus> resolveSearchUserStatuses(List<UserStatus> userStatuses) {
        if (userStatuses == null || userStatuses.isEmpty()) {
            return DEFAULT_SEARCH_STATUSES;
        }
        return userStatuses;
    }

}
