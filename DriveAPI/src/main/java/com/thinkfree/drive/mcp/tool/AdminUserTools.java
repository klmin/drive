package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.v1.admin.auditlog.enums.AuditEventType;
import com.thinkfree.drive.api.v1.admin.auditlog.request.AuditLogSearchRequest;
import com.thinkfree.drive.api.v1.admin.auditlog.usecase.AuditLogQueryUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserImportUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserQueryUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkStatusCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserListCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserSearchCommand;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.mcp.security.McpToolTenantUser;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.AdminUserToolConverter;
import com.thinkfree.drive.mcp.tool.request.UserUpsertToolItem;
import com.thinkfree.drive.mcp.tool.response.*;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 관리자(ADMIN) 전용 MCP 툴 — 웹 AdminUserController(/api/v1/admins/users)와 1:1 대응,
 * application.admin.user 의 AdminUserUseCase·AdminUserQueryUseCase·AdminUserImportUseCase를 호출한다 (사용자 목록·검색·스토리지 사용량·상태 일괄 조회 + 즉시 완전 삭제·일괄 등록/수정).
 * 감사 로그(listUserLogs)만 예외로 웹 AuditLogController(/api/v1/admins/audit-logs, 거버넌스 프록시)와 대응하며 AuditLogQueryUseCase를 호출한다
 * — 그 유스케이스는 governance.audit.enabled=true일 때만 빈이 있어 ObjectProvider로 받는다(꺼져 있으면 인밴드 에러).
 * 즉시 삭제(deleteUser)·상태 일괄 조회(getUsersStatus)·일괄 등록/수정(bulkUpsertUsers)은 웹 관리자 화면에는 없는 외부 연동용 API(@ExternalApi) 경로
 * — 삭제는 유예 없이 cascade 삭제 후 STATUS=DELETED, 일괄 등록/수정은 웹 계약과 완전 동형(role 가드 없음·수정은 전체 교체, 사용자 결정 09-10).
 * 접근 제어·테넌트·에러 규약은 {@link AdminTools} 클래스 주석과 동일.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.ADMIN)
public class AdminUserTools {

    private final AdminUserUseCase adminUserUseCase;
    private final AdminUserQueryUseCase adminUserQueryUseCase;
    private final AdminUserImportUseCase adminUserImportUseCase;
    private final AdminUserToolConverter adminUserToolConverter;
    private final ObjectProvider<AuditLogQueryUseCase> auditLogQueryUseCaseProvider;

    private static final int USER_NAME_MAX_LENGTH = 20; // TB_USER.USER_NAME varchar(20) — AdminTools와 동일 상수

    @Tool(name = "drive_admin_list_users",
            description = "관리자 전용 — 조직(테넌트)의 계정 목록(사용자·관리자)을 조회한다 (웹 관리자 화면의 계정 목록과 동일 데이터). "
                    + "'사용자 목록 보여줘', '관리자가 누구야?', '비활성 계정 찾아줘', '이 이메일 계정 있어?'처럼 조직 계정 현황이 필요할 때 사용한다. "
                    + "roles로 역할 필터(USER/ADMIN, 여러 개면 하나라도 보유한 계정)가 가능하고, 생략하면 사용자·관리자 모두 반환한다. "
                    + "한 계정이 사용자·관리자 역할을 같이 가질 수 있어 어느 필터에도 나올 수 있다. "
                    + "항목에는 userSeq·userId(이메일)·userName·status(ACTIVE/INACTIVE)·roles(보유 역할 목록)·등록/수정/마지막 로그인 일시·"
                    + "isProtected(보호 계정 — 테넌트 생성 때 만든 계정, 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·비밀번호 변경이 거부됨)가 포함되며, "
                    + "여기서 얻은 userSeq를 다른 관리자 툴의 대상 사용자 식별자로 사용한다. "
                    + "status로 상태 필터(ACTIVE/INACTIVE — 그 외 값은 에러), userId로 이메일 부분 일치 필터가 가능하다. "
                    + "이미 알고 있는 여러 이메일의 존재·상태만 한 번에 확인할 때는 drive_admin_get_users_status가 더 적합하다. "
                    + "결과는 커서 기반 페이지네이션(userSeq 오름차순 고정)이며, 응답 hasNext가 true면 nextCursor를 "
                    + "다음 호출의 cursorId로 그대로 넘기면 다음 페이지를 받는다. "
                    + "이 툴은 조회 전용이며 계정 생성/수정/비활성화는 하지 않는다.")
    public McpToolResult<AdminUserListToolResponse> listUsers(
            @ToolParam(description = "계정 상태 필터: ACTIVE(활성), INACTIVE(비활성). 미지정 시 표시 대상 상태 전체", required = false) UserStatus status,
            @ToolParam(description = "사용자 ID(이메일) 부분 일치 필터. 미지정 시 전체", required = false) String userId,
            @ToolParam(description = "역할 필터: USER(사용자), ADMIN(관리자). 여러 개면 하나라도 보유한 계정. 미지정 시 USER·ADMIN 전체", required = false) List<RoleType> roles,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 — 직전 응답의 nextCursor (마지막 항목의 userSeq). 첫 페이지면 생략", required = false) Long cursorId) {

        log.info("[MCP] drive_admin_list_users called. status : {}, userId : {}, pageSize : {}, cursorId : {}",
                status, userId, pageSize, cursorId);

        return McpToolResult.executeAuthed(details -> {

            var command = AdminUserListCommand.builder()
                    .status(status)
                    .userId(userId)
                    .roles(roles)
                    .cursorId(cursorId)
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            return adminUserToolConverter.toListResponse(adminUserQueryUseCase.findDisplayableUsersPaginated(command));
        });
    }

    @Tool(name = "drive_admin_get_user_storage_usage",
            description = "관리자 전용 — 특정 사용자 한 명의 스토리지 사용량을 조회한다 (웹 관리자 화면의 사용자 용량 정보와 동일 데이터). "
                    + "'이 사용자 용량 얼마나 썼어?', '누가 저장공간 꽉 찼어?'처럼 개별 계정의 스토리지 현황이 필요할 때 사용한다. "
                    + "대상 userSeq는 drive_admin_list_users 또는 drive_admin_search_users로 먼저 조회해 얻는다. "
                    + "반환 값은 모두 byte 단위: totalQuotaBytes(할당 총량), totalUsedBytes(총 사용량), "
                    + "liveUsedBytes(현재 파일), versionUsedBytes(버전 이력), trashUsedBytes(휴지통). "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "이 툴은 조회 전용이며 용량 할당량을 변경하지 않는다.")
    public McpToolResult<AdminUserStorageUsageToolResponse> getUserStorageUsage(@McpToolTenantUser @ToolParam(description = "대상 사용자 식별자(userSeq). drive_admin_list_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_get_user_storage_usage called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details ->
                adminUserToolConverter.toStorageUsageResponse(adminUserQueryUseCase.storageUsage(userSeq)));
    }

    @Tool(name = "drive_admin_list_user_logs",
            description = "관리자 전용 — 조직의 감사 로그를 조회한다. 로그인·로그아웃·계정 잠금, 파일·폴더 작업, 공유, 사용자 관리, 설정 변경 활동을 이벤트 단위로 최신순 반환하며, 호출한 관리자가 속한 조직의 로그만 조회된다. "
                    + "'이 사용자 최근 활동 보여줘', '어제 누가 파일 삭제했어?', '로그인 실패 기록 있어?', 'MCP로 한 작업 보여줘'처럼 활동 이력이 필요할 때 사용한다. "
                    + "필터를 모두 생략하면 전체 이벤트를 반환한다. "
                    + "특정 사용자의 활동은 actorEmail(작업을 수행한 사용자의 이메일, 대소문자 무시 부분 일치 — 'admin'은 'admin@example.com'도 포함)로 거른다 — 이메일을 모르면 drive_admin_search_users로 먼저 얻는다. "
                    + "관리자가 다른 사용자에게 한 작업(비밀번호 변경 등)은 관리자 이메일로 기록되고, 대상 사용자는 항목의 objectEmail에 담긴다. "
                    + "eventCategory와 eventTypes를 함께 주면 두 조건을 모두 만족하는 이벤트만 반환되므로 보통 하나만 쓴다 — 분류 하나는 eventCategory, "
                    + "여러 분류나 개별 이벤트는 eventTypes(분류별 전체 값은 eventTypes 파라미터 설명 참고)로 지정한다. "
                    + "eventCategory·eventTypes·channel은 정해진 값만 허용되며(파라미터 스키마 참고) 그 밖의 값은 호출이 거부된다. "
                    + "로그인 성공·실패는 모두 eventType LOGIN으로 기록되며 outcomeStatus(SUCCESS/FAIL)로 구분한다. "
                    + "항목에는 eventId·occurredAt(발생 일시)·eventType·eventCategory·actorType·actorName·actorEmail·objectType(FILE/FOLDER/USER/SETTING)·objectName·objectEmail·"
                    + "objectPath·outcomeStatus·outcomeCode(실패 사유)·channel(WEB/API/MCP/SYSTEM)·clientIp·tenantSeq·tenantName·change(변경 전/후)·targets(공유 대상 등)가 포함된다. "
                    + "결과는 페이지 번호 방식이며 pageIndex×pageSize < totalCount면 pageIndex를 1 올려 다음 페이지를 받는다. "
                    + "감사 로그 기능이 활성화되지 않은 환경에서는 error를, 감사 로그 서버 조회가 실패하면 error(AUDIT_LOG_001 — 잠시 후 재시도)를 반환한다. 이 툴은 조회 전용이다.")
    public McpToolResult<AdminAuditLogListToolResponse> listUserLogs(
            @ToolParam(description = "작업을 수행한 사용자의 이메일(userId), 대소문자 무시 부분 일치(한 사용자만 보려면 전체 이메일 지정). 미지정 시 전체 사용자", required = false) String actorEmail,
            @ToolParam(description = "기간 시작일 (yyyy-MM-dd, 포함). 미지정 시 시작 제한 없음", required = false) String from,
            @ToolParam(description = "기간 종료일 (yyyy-MM-dd, 포함). 미지정 시 종료 제한 없음", required = false) String to,
            @ToolParam(description = "이벤트 분류: LOGIN, FILE_FOLDER, SHARE, USER_MANAGEMENT, SETTING 중 하나. 미지정 시 전체", required = false) EventCategory eventCategory,
            @ToolParam(description = "이벤트 종류(여러 개면 OR). 분류별 전체 값 — "
                    + "LOGIN: LOGIN, LOGOUT, ACCOUNT_LOCKED_STATUS_CHANGE / "
                    + "FILE_FOLDER: FILE_CREATE, FILE_UPLOAD, FILE_DOWNLOAD, FILE_PREVIEW, FILE_OPEN, FILE_EDIT, FILE_DELETE, FILE_PERMANENT_DELETE, FILE_RESTORE, FILE_RENAME, FILE_MOVE, FILE_COPY, "
                    + "FOLDER_CREATE, FOLDER_DOWNLOAD, FOLDER_DELETE, FOLDER_PERMANENT_DELETE, FOLDER_RESTORE, FOLDER_RENAME / "
                    + "SHARE: SHARE_CREATE, SHARE_PERMISSION_CHANGE, SHARE_REMOVE / "
                    + "USER_MANAGEMENT: USER_CREATE, USER_DELETE, USER_PERMANENT_DELETE, USER_RESTORE, USER_INFO_UPDATE, USER_TWOFACTOR_RESET, USER_PASSWORD_CHANGE, USER_STATUS_CHANGE, "
                    + "ADMIN_CREATE, ADMIN_DELETE, ADMIN_PERMANENT_DELETE, ADMIN_RESTORE, ADMIN_INFO_UPDATE, ADMIN_PASSWORD_CHANGE, ADMIN_STATUS_CHANGE / "
                    + "SETTING: SETTING_UPDATE, SETTING_ALLOWED_IP_CHANGE. 미지정 시 전체", required = false) List<AuditEventType> eventTypes,
            @ToolParam(description = "유입 경로 필터: WEB, API, MCP, SYSTEM. 미지정 시 전체", required = false) AccessMethod channel,
            @ToolParam(description = "처리 결과 필터: SUCCESS, FAIL. 미지정 시 전체", required = false) String outcomeStatus,
            @ToolParam(description = "페이지 번호 (1부터). 기본값 1", required = false) Integer pageIndex,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize) {

        log.info("[MCP] drive_admin_list_user_logs called. actorEmail : {}, from : {}, to : {}, eventCategory : {}, eventTypes : {}, channel : {}, outcomeStatus : {}, pageIndex : {}, pageSize : {}",
                actorEmail, from, to, eventCategory, eventTypes, channel, outcomeStatus, pageIndex, pageSize);

        AuditLogQueryUseCase auditLogQueryUseCase = auditLogQueryUseCaseProvider.getIfAvailable();
        if (auditLogQueryUseCase == null) {
            return McpToolResult.error("감사 로그 연동이 활성화되어 있지 않아 감사 로그를 조회할 수 없습니다.");
        }

        return McpToolResult.executeAuthed(details -> {

            // 웹 AuditLogController.list 동형 — 필터 해석·테넌트 격리(scopeId)·기간 경계 변환은 유스케이스가 그대로 수행
            var request = new AuditLogSearchRequest(
                    actorEmail,
                    McpToolParams.parseDate(from, "from"),
                    McpToolParams.parseDate(to, "to"),
                    eventCategory,
                    eventTypes,
                    channel,
                    outcomeStatus);
            var paging = new PagingRequest(McpToolParams.normalizePageIndex(pageIndex), McpToolParams.normalizePageSize(pageSize));

            return adminUserToolConverter.toAuditLogListResponse(auditLogQueryUseCase.search(request, paging));
        });
    }

    @Tool(name = "drive_admin_search_users",
            description = "관리자 전용 — 조직(테넌트)의 사용자를 이름 또는 이메일로 검색한다 (웹 관리자 화면의 사용자 검색과 동일 데이터). "
                    + "'김철수 계정 찾아줘', 'kyungmin으로 시작하는 계정 있어?'처럼 특정 사용자의 userSeq를 알아내 "
                    + "다른 관리자 툴(스토리지 사용량 등)의 대상으로 넘기거나, 감사 로그 조회(drive_admin_list_user_logs)에 쓸 이메일(userId)을 확인할 때 사용한다. "
                    + "검색어는 userId(이메일)·userName 중 하나만 부분 일치해도 결과에 포함된다. "
                    + "활성(ACTIVE) 계정만 반환하고, roles를 생략하면 일반 사용자(USER) 역할을 가진 계정만 찾는다 — "
                    + "관리자를 찾으려면 roles에 ADMIN을(사용자·관리자 모두면 USER와 ADMIN을) 넘긴다. "
                    + "한 계정이 사용자·관리자 역할을 같이 가질 수 있어 어느 역할로 찾아도 나올 수 있다. "
                    + "비활성 계정까지 봐야 하면 drive_admin_list_users를, 이메일 여러 개의 상태를 한 번에 확인하려면 drive_admin_get_users_status를 사용한다. "
                    + "항목에는 userSeq·userId(이메일)·userName·status·roles(보유 역할 목록)·isProtected(보호 계정 여부)가 포함되며, 페이지네이션 없이 전체 결과를 반환한다. "
                    + "결과가 여러 명(동명이인)이면 임의로 고르지 말고 후보(이름·이메일)를 사용자에게 보여주고 확인받는다. "
                    + "이 툴은 조회 전용이며 계정을 변경하지 않는다.")
    public McpToolResult<AdminUserSearchToolResponse> searchUsers(
            @ToolParam(description = "검색어 — 사용자 이름 또는 이메일(부분 일치)") String searchKeyword,
            @ToolParam(description = "역할 필터: USER(사용자), ADMIN(관리자). 여러 개면 하나라도 보유한 계정. 미지정 시 USER", required = false) List<RoleType> roles) {

        log.info("[MCP] drive_admin_search_users called. searchKeyword : {}", searchKeyword);

        return McpToolResult.executeAuthed(details -> {

            McpToolParams.requireText(searchKeyword, "searchKeyword");

            return adminUserToolConverter.toSearchResponse(adminUserQueryUseCase.search(new AdminUserSearchCommand(searchKeyword, roles, null)));
        });
    }

    @Tool(name = "drive_admin_get_users_status",
            description = "관리자 전용 — 여러 사용자의 계정 상태를 이메일(userId) 목록으로 한 번에 조회한다 (외부 연동용 관리자 API '사용자 상태 일괄 조회'와 요청·응답 동일). "
                    + "'이 이메일들 계정 상태 확인해줘', '명단에서 비활성인 사람 찾아줘', '이 명단 중 등록 안 된 사람 있어?'처럼 "
                    + "이미 알고 있는 여러 이메일의 존재 여부·활성 상태를 한 번에 볼 때 사용한다 — "
                    + "한 명만 확인할 때는 drive_admin_search_users 또는 drive_admin_get_user를, 조직 전체 명단이 필요하면 drive_admin_list_users를 사용한다. "
                    + "응답은 조회된 계정 항목의 배열이며 각 항목은 userSeq·userId(이메일)·status(ACTIVE/INACTIVE)를 가진다. "
                    + "일반 사용자(USER) 중 활성·비활성 계정만 반환하고, 요청한 userId 중 응답에 없는 것은 존재하지 않거나 관리자 계정이거나 "
                    + "삭제 대기(PENDING_DELETE)/삭제 완료(DELETED) 상태다 — 이 네 경우를 구분해 주지 않으므로 응답에 없는 이메일은 "
                    + "'비활성'으로 해석하지 말고 '조회되지 않음'으로 그대로 사용자에게 알린다(상세 확인은 drive_admin_get_user·drive_admin_list_pending_delete_users). "
                    + "userId는 정확히 일치해야 하며(부분 일치 아님), 응답 순서는 요청 순서와 무관하고, 같은 이메일을 중복 요청해도 1건만 반환된다. "
                    + "이 툴은 조회 전용이며 계정을 변경하지 않는다.")
    public McpToolResult<List<AdminUserBulkStatusToolResponse>> getUsersStatus(
            @ToolParam(description = "조회할 사용자 이메일(userId) 배열 — 1개 이상, 정확 일치") List<String> userIds) {

        log.info("[MCP] drive_admin_get_users_status called. userIds : {}", userIds);

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.requireNonEmpty(userIds, "userIds"); // 웹 @NotEmpty 등가 — 요소 공백·중복 정리는 하지 않음(웹 동형)
            // 테넌트 경계는 usecase의 TenantContext가 강제(웹 동일). 응답은 웹 ApiResponse.success(List) 동형으로 배열을 그대로 data에 담는다.
            return adminUserToolConverter.toBulkStatusResponse(adminUserQueryUseCase.findUserStatusByUserIds(new AdminUserBulkStatusCommand(userIds)));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_delete_user",
            description = "관리자 전용 — 사용자 계정을 즉시 완전 삭제한다 (외부 연동용 관리자 API와 동일 동작, 웹 관리자 화면에는 없는 경로). "
                    + "'이 계정 완전히 삭제해줘', '삭제 대기 안 거치고 바로 지워줘'처럼 사용자가 즉시·완전 삭제를 명시적으로 요청한 경우에만, "
                    + "대상 계정(userSeq·userId·userName)을 사용자에게 확인시킨 후 사용한다. "
                    + "⚠️ 가장 파괴적인 작업이다 — 보관 기간 없이 계정의 리소스·공유(준 것·받은 것)·버전·휴지통·즐겨찾기·최근 문서·알림·토큰·API Key가 모두 삭제되고 "
                    + "저장소 파일도 제거되며, status가 DELETED로 바뀐다. 복구 불가(drive_admin_restore_user 대상이 아님). "
                    + "삭제된 계정의 이메일(userId)은 같은 테넌트에서 다시 등록에 쓸 수 있다(비활성 계정의 이메일은 재사용 불가). "
                    + "단순 '삭제해줘'·'탈퇴시켜줘'는 이 툴이 아니라 drive_admin_withdraw_user(30일 보관 후 삭제, 복원 가능)를 사용하고, "
                    + "로그인만 막으려면 drive_admin_deactivate_user를 사용한다. "
                    + "활성(ACTIVE)·비활성(INACTIVE)·삭제 대기(PENDING_DELETE) 계정 모두 대상이 되지만, 본인 계정과 기본 관리자 계정은 삭제할 수 없다(에러 USER_012). "
                    + "보호 계정(isProtected=true — 테넌트 생성 때 만든 관리자 계정, 테넌트 유형 무관)도 삭제할 수 없다(USER_034). "
                    + "이미 삭제 완료(DELETED)된 계정이면 에러(ADMIN_USER_003)가 반환된다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "대상 userSeq는 drive_admin_list_users·drive_admin_search_users·drive_admin_list_pending_delete_users로 먼저 조회해 얻는다. "
                    + "삭제된 계정은 목록 툴에 나오지 않지만 drive_admin_get_user로는 DELETED 상태로 조회된다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> deleteUser(
            @McpToolTenantUser @ToolParam(description = "완전 삭제할 사용자 식별자(userSeq). drive_admin_list_users·drive_admin_search_users·drive_admin_list_pending_delete_users 응답의 userSeq") Integer userSeq) {

        log.info("[MCP] drive_admin_delete_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            // 웹 외부 API 경로에는 본인·기본 관리자 차단이 없다(usecase·서비스 모두) — withdraw(USER_012)와 대칭이 되도록 MCP 전용 가드로 막는다(사용자 결정 09-09, A안).
            if (Objects.equals(details.getUserSeq(), userSeq) || userSeq == DriveConstants.SUPER_ADMIN_USER_SEQ) {
                throw ApiRuntimeException.type(UserErrorCode.CANNOT_DELETE_SELF_OR_DEFAULT_ADMIN);
            }
            // 존재X(USER_005)·이미 DELETED(ADMIN_USER_003)는 웹과 동일하게 서비스가 검증한다(인밴드 변환).
            adminUserUseCase.deleteUser(userSeq, details.getUserSeq());
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_bulk_upsert_users",
            description = "관리자 전용 — 여러 사용자 계정을 한 번에 등록하거나 수정한다 (외부 연동용 관리자 API '사용자 일괄 등록/수정'과 요청·응답·규칙 동일, 웹 관리자 화면에는 없는 경로). "
                    + "'이 명단으로 계정 일괄 생성해줘', '이 사용자들 용량 한 번에 늘려줘'처럼 사용자가 여러 계정의 등록·수정을 명시적으로 요청한 경우에만, "
                    + "처리할 항목 목록(이메일·이름·용량 등)을 사용자에게 확인시킨 후 사용한다 — 한 명이면 drive_admin_register_user(등록)·drive_admin_update_user(수정)를 사용한다. "
                    + "항목의 userSeq가 없으면 신규 등록(userId·password·roles 필수), 있으면 그 계정의 수정이다 — 한 호출에 등록과 수정을 섞어도 된다. "
                    + "할당 용량(totalQuotaByte)은 역할에 USER가 있으면 필수(0보다 커야 함), ADMIN만이면 생략 또는 0(0이 아니면 QUOTA_009, 수정 항목은 수정 후 역할 기준)이다. "
                    + "신규 합 + 수정 증감 합(감소분 상쇄)이 테넌트 총 용량의 남은 할당 가능량을 넘으면 거부된다(QUOTA_007). "
                    + "⚠️ 수정 항목은 roles를 뺀 나머지 필드가 '생략=유지'가 아니라 전체 교체다(roles는 생략하면 역할 유지) — userName·totalQuotaByte(USER)는 필수이고, countryCode·phoneNumber를 생략하면 기존 전화번호가 지워지므로 "
                    + "유지하려면 drive_admin_get_user로 현재 값을 확인해 다시 넣는다. 용량은 현재 사용량보다 작게 줄일 수 없다(USER_014). "
                    + "⚠️ 전체가 하나의 트랜잭션이다 — 항목 하나라도 실패하면(같은 테넌트에 있거나 요청 안에서 겹치는 이메일 USER_004·수정 대상이 없거나 다른 테넌트 USER_005·좌석 부족 USER_013·"
                    + "이메일 형식 USER_002·비밀번호 정책 USER_001·전화 한쪽만 USER_027·USER 용량 누락 USER_026·스토리지 상한 QUOTA_004·테넌트 총 용량 초과 QUOTA_007·"
                    + "0 이하 용량 QUOTA_008·관리자 용량 지정 QUOTA_009·사용 중인 용량이 있는 계정의 USER 회수 USER_030·본인 ADMIN 회수 USER_031·"
                    + "시스템 관리형 단일 사용자 테넌트(SYSTEM_MANAGED + SINGLE)의 등록 항목 TENANT_011, 시스템 관리형 테넌트(SYSTEM_MANAGED) 보호 계정의 수정 항목 TENANT_011,보호 계정의 roles 변경 USER_034 등) "
                    + "전부 롤백되고 아무 계정도 만들어지거나 바뀌지 않는다. "
                    + "이때 서버 에러는 어느 항목이 문제인지 알려주지 않으므로 사용자에게 에러 내용을 그대로 전달하고 항목을 함께 점검한다"
                    + "(툴 자체의 필수값 검증 실패만 users[i].필드명 형식으로 항목을 가리킨다). "
                    + "roles는 USER·ADMIN 중 1개 이상이다(등록 항목의 빈 목록은 에러 \"users[i].roles는 1개 이상 지정해야 합니다.\", "
                    + "그 외 값·수정 항목의 빈 목록은 에러 \"users[i].roles은 [ADMIN, USER] 중 1개 이상이어야 합니다: [입력값]\") — 수정 항목에 roles를 주면 보유 역할을 그 목록으로 교체한다. "
                    + "국가코드와 전화번호는 한 쌍이다 — 지정하려면 둘을 함께 보내고(한쪽만 보내면 USER_027), 등록 시 생략하면 미설정으로 등록된다. "
                    + "⚠️ password는 계정의 초기 비밀번호 원문이다 — 대화 기록에 평문으로 남으므로 실제 쓸 비밀번호가 아닌 임시 비밀번호를 지정하고, "
                    + "등록 후 사용자가 직접 변경하도록 안내한다. "
                    + "성공 시 insertedCount·updatedCount와 신규 등록된 계정의 insertedUsers[{userId, userSeq}]를 반환한다(수정 항목은 개수로만 집계).")
    public McpToolResult<AdminUserBulkUpsertToolResponse> bulkUpsertUsers(
            @ToolParam(description = "등록/수정할 사용자 항목 배열 — 1개 이상. 항목: userSeq(생략=신규 등록, 지정=수정)·userId(등록 필수, 이메일)·"
                    + "password(등록 필수, 원문)·userName(필수, 최대 20자)·totalQuotaByte(byte — 역할에 USER 있으면 필수·0보다 커야 함, ADMIN만이면 생략 또는 0)·roles(등록 필수, USER/ADMIN 목록, 수정 시 생략=유지)·countryCode·phoneNumber(함께 지정, 수정 시 생략하면 삭제)") List<UserUpsertToolItem> users) {

        // 비밀번호는 로그에 남기지 않는다 — 항목의 식별 필드만 요약
        log.info("[MCP] drive_admin_bulk_upsert_users called. users : {}",
                users == null ? null : users.stream().map(u -> u == null ? null : "userSeq=" + u.userSeq() + ",userId=" + u.userId()).toList());

        return McpToolResult.executeAuthed(details -> {
            McpToolParams.requireNonEmpty(users, "users"); // 웹 @NotEmpty 등가

            // 웹 @Valid(항목 @NotBlank userName) 등가 + 등록 필수 3종(usecase USER_026)·roles 값을 항목 index와 함께 선검증한다
            // — 의미는 usecase와 동일하고 메시지만 항목을 가리킨다. 이메일 형식·중복·좌석·quota(역할별 필수·관리자 0)·전화 동반·테넌트 경계는 웹과 동일하게 usecase가 검증한다(인밴드 변환).
            List<UserItemCommand> items = new ArrayList<>(users.size());
            for (int i = 0; i < users.size(); i++) {
                UserUpsertToolItem item = users.get(i);
                String prefix = "users[" + i + "].";
                McpToolParams.require(item, "users[" + i + "]");
                McpToolParams.requireText(item.userName(), prefix + "userName");
                McpToolParams.requireMaxLength(item.userName(), USER_NAME_MAX_LENGTH, prefix + "userName"); // varchar(20) 초과 시 SQL 예외라 선검증
                if (item.userSeq() == null) {
                    McpToolParams.requireText(item.userId(), prefix + "userId");
                    McpToolParams.requireText(item.password(), prefix + "password");
                    McpToolParams.requireNonEmpty(item.roles(), prefix + "roles"); // usecase 와 같은 허용 범위(USER/ADMIN) — 메시지만 항목을 가리킨다
                }
                if (item.roles() != null) {
                    requireRegistrableRoles(item.roles(), prefix + "roles"); // usecase 와 같은 허용 범위(USER/ADMIN 1개 이상) — 메시지만 항목을 가리킨다
                }
                items.add(new UserItemCommand(item.userSeq(), item.userId(), item.password(), item.userName(),
                        item.totalQuotaByte(), item.roles() != null ? new HashSet<>(item.roles()) : null, item.countryCode(), item.phoneNumber()));
            }

            return adminUserToolConverter.toBulkUpsertResponse(
                    adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(items), details.getUserSeq()));
        });
    }

    // 일괄 등록/수정 역할은 단건과 같이 등록 가능한 역할(USER/ADMIN) 1개 이상 — usecase 도 같은 규칙(USER_028)이고, MCP 는 허용 값 목록을 인밴드로 돌려준다.
    private static void requireRegistrableRoles(List<RoleType> roles, String name) {
        if (!RoleType.isAssignable(roles)) {
            throw new IllegalArgumentException(name + "은 " + Arrays.toString(Arrays.stream(RoleType.values()).filter(RoleType::isRegistrable).toArray()) + " 중 1개 이상이어야 합니다: " + roles);
        }
    }

}
