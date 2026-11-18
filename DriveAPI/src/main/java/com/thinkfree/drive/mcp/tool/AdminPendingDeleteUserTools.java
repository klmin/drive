package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.admin.user.pendingdelete.AdminPendingDeleteUserUseCase;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeleteUserListCommand;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.mcp.security.McpToolTenantUser;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.AdminUserToolConverter;
import com.thinkfree.drive.mcp.tool.response.AdminPendingDeleteUserListToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 관리자(ADMIN) 전용 MCP 툴 — 웹 AdminPendingDeleteUserController(/api/v1/admins/users/pending-delete)와 1:1 대응,
 * application.admin.user.pendingdelete.AdminPendingDeleteUserUseCase를 호출한다 (삭제 대기 계정 목록·복원).
 * 배열 완전 삭제(permanentDeleteUsers)는 MCP 미노출(웹 관리자 화면 전용) — 단건 즉시 삭제는 {@link AdminUserTools#deleteUser}(외부 API 경로, DELETE_USER_TOOL_HANDOFF.md).
 * 접근 제어·테넌트·에러 규약은 {@link AdminTools} 클래스 주석과 동일.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.ADMIN)
public class AdminPendingDeleteUserTools {

    private final AdminPendingDeleteUserUseCase adminPendingDeleteUserUseCase;
    private final AdminUserToolConverter adminUserToolConverter;

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_admin_restore_user",
            description = "관리자 전용 — 탈퇴(삭제 대기, PENDING_DELETE) 상태의 사용자 계정을 복원한다 (웹 관리자 화면 '삭제 대기 사용자' 목록의 복원 버튼과 동일 동작). "
                    + "'탈퇴 처리한 계정 되돌려줘', '삭제 대기 계정 복원해줘'처럼 drive_admin_withdraw_user로 탈퇴 처리된 계정을 보관 기간(약 30일) 내에 되살릴 때 사용한다. "
                    + "복원 결과는 비활성(INACTIVE) 상태다 — 로그인까지 가능하게 하려면 복원 후 drive_admin_activate_user로 활성화한다. "
                    + "삭제 대기(PENDING_DELETE) 상태인 계정만 대상이 되며, 그 외 상태(ACTIVE·INACTIVE 등)면 에러(ADMIN_USER_001)가 반환된다 — "
                    + "삭제 대기 계정은 drive_admin_list_users·drive_admin_search_users에 나오지 않으므로 대상 탐색은 "
                    + "drive_admin_list_pending_delete_users로, 개별 상태 확인은 drive_admin_get_user 상세 조회로 한다. "
                    + "다른 테넌트에 속하거나 존재하지 않는 userSeq면 '사용자를 찾을 수 없음' 에러를 반환한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다(성공 시 본문 없음).")
    public McpToolResult<Void> restoreUser(
            @McpToolTenantUser @ToolParam(description = "복원할 사용자 식별자(userSeq). 탈퇴 처리 시 사용한 userSeq 또는 drive_admin_get_user로 확인") Integer userSeq) {

        log.info("[MCP] drive_admin_restore_user called. userSeq : {}", userSeq);

        return McpToolResult.executeAuthed(details -> {
            // 삭제 대기 상태 아님(ADMIN_USER_001)은 웹과 동일하게 usecase/도메인이 검증한다(인밴드 변환).
            adminPendingDeleteUserUseCase.restore(userSeq);
            return null; // 웹 응답(ApiResponse.success(), 본문 없음) 동형
        });
    }

    @Tool(name = "drive_admin_list_pending_delete_users",
            description = "관리자 전용 — 탈퇴 처리되어 삭제 대기(PENDING_DELETE) 중인 사용자 계정 목록을 조회한다 "
                    + "(웹 관리자 화면의 '삭제 대기 사용자' 목록과 동일 데이터). "
                    + "'탈퇴 처리된 계정 목록 보여줘', '곧 완전 삭제되는 계정 있어?'처럼 삭제 대기 계정 현황이 필요할 때, "
                    + "또는 drive_admin_restore_user로 복원할 대상 userSeq를 찾을 때 사용한다 — "
                    + "삭제 대기 계정은 drive_admin_list_users·drive_admin_search_users에는 나오지 않는다. "
                    + "항목에는 userSeq·userId(이메일)·userName·status(항상 PENDING_DELETE)·roles(보유 역할 목록)·pendingDeleteDate(탈퇴 처리 일시)·"
                    + "deleteScheduleTime(완전 삭제 예정 일시)·deleteUserSeq/deleteUserId/deleteUserStatus(탈퇴 처리를 수행한 관리자)가 포함된다. "
                    + "일반 사용자와 관리자 계정 모두 포함된다. "
                    + "pendingDeleteStartDate/pendingDeleteEndDate(탈퇴 처리일 범위)·deleteScheduleStartDate/deleteScheduleEndDate(삭제 예정일 범위)로 "
                    + "일 단위 필터가 가능하며, 각 범위는 시작·종료를 함께 지정해야 한다(종료일 당일 포함). "
                    + "결과는 커서 기반 페이지네이션(pendingDeleteDate 내림차순 — 최근 탈퇴 처리 순)이며, 응답 hasNext가 true면 "
                    + "nextCursor를 cursorId로, nextCursorValue를 cursorValue로 둘 다 다음 호출에 그대로 넘기면 다음 페이지를 받는다. "
                    + "이 툴은 조회 전용이며 계정 상태를 변경하지 않는다.")
    public McpToolResult<AdminPendingDeleteUserListToolResponse> listPendingDeleteUsers(
            @ToolParam(description = "탈퇴 처리일 범위 시작(yyyy-MM-dd). pendingDeleteEndDate와 함께 지정. 미지정 시 전체", required = false) String pendingDeleteStartDate,
            @ToolParam(description = "탈퇴 처리일 범위 종료(yyyy-MM-dd, 당일 포함). pendingDeleteStartDate와 함께 지정", required = false) String pendingDeleteEndDate,
            @ToolParam(description = "완전 삭제 예정일 범위 시작(yyyy-MM-dd). deleteScheduleEndDate와 함께 지정. 미지정 시 전체", required = false) String deleteScheduleStartDate,
            @ToolParam(description = "완전 삭제 예정일 범위 종료(yyyy-MM-dd, 당일 포함). deleteScheduleStartDate와 함께 지정", required = false) String deleteScheduleEndDate,
            @ToolParam(description = "페이지 크기 (1~100). 기본값 20", required = false) Integer pageSize,
            @ToolParam(description = "다음 페이지 커서 — 직전 응답의 nextCursor (마지막 항목의 userSeq). cursorValue와 함께 지정. 첫 페이지면 생략", required = false) Long cursorId,
            @ToolParam(description = "다음 페이지 커서 값 — 직전 응답의 nextCursorValue (마지막 항목의 pendingDeleteDate 문자열). cursorId와 함께 지정. 첫 페이지면 생략", required = false) String cursorValue) {

        log.info("[MCP] drive_admin_list_pending_delete_users called. pendingDelete : {}~{}, deleteSchedule : {}~{}, pageSize : {}, cursorId : {}, cursorValue : {}",
                pendingDeleteStartDate, pendingDeleteEndDate, deleteScheduleStartDate, deleteScheduleEndDate, pageSize, cursorId, cursorValue);

        return McpToolResult.executeAuthed(details -> {
            // 하부 SQL은 범위·커서 모두 양쪽 값이 있을 때만 조건을 적용하고 한쪽만 오면 조용히 무시한다(웹은 화면이 항상 쌍으로 보냄).
            // MCP는 LLM이 한쪽만 넘기기 쉬워 "필터가 안 걸린 전체 목록"을 필터 결과로 오독할 수 있으므로 쌍 지정을 인밴드로 강제한다.
            McpToolParams.requireTogether(pendingDeleteStartDate, pendingDeleteEndDate, "pendingDeleteStartDate", "pendingDeleteEndDate");
            McpToolParams.requireTogether(deleteScheduleStartDate, deleteScheduleEndDate, "deleteScheduleStartDate", "deleteScheduleEndDate");
            McpToolParams.requireTogether(cursorId, cursorValue, "cursorId", "cursorValue");

            var command = AdminPendingDeleteUserListCommand.builder()
                    .pendingDeleteStartDate(McpToolParams.parseDate(pendingDeleteStartDate, "pendingDeleteStartDate"))
                    .pendingDeleteEndDate(McpToolParams.parseDate(pendingDeleteEndDate, "pendingDeleteEndDate"))
                    .deleteScheduleStartDate(McpToolParams.parseDate(deleteScheduleStartDate, "deleteScheduleStartDate"))
                    .deleteScheduleEndDate(McpToolParams.parseDate(deleteScheduleEndDate, "deleteScheduleEndDate"))
                    .cursorId(cursorId)
                    // 형식 검증 후 툴 출력 포맷으로 정규화 — 문자열 그대로 넘기면 쓰레기 값은 SQL 예외(isError 노출), 날짜만은 자정 해석으로 당일 항목 조용한 누락
                    .cursorValue(McpToolParams.normalizeCursorDateTime(cursorValue, "cursorValue"))
                    .pageSize(McpToolParams.normalizePageSize(pageSize))
                    .build();

            return adminUserToolConverter.toPendingDeleteListResponse(adminPendingDeleteUserUseCase.findAllCursorPaginated(command));
        });
    }

}
