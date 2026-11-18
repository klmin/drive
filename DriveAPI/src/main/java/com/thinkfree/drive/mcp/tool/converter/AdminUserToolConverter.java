package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.api.page.ApiPage;
import com.thinkfree.drive.api.page.response.PaginationResponse;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogItemResponse;
import com.thinkfree.drive.application.admin.info.AdminUserDetailInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkStatusInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkUpsertInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserListInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserSearchInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserStorageUsageInfo;
import com.thinkfree.drive.application.admin.user.pendingdelete.info.AdminPendingDeleteUserListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.mcp.tool.response.AdminAuditLogListToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminPendingDeleteUserListToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserBulkStatusToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserBulkUpsertToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserDetailToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserListToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserSearchToolResponse;
import com.thinkfree.drive.mcp.tool.response.AdminUserStorageUsageToolResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 AdminUserResponseConverter 등가물.
 * 항목 필드는 AdminUserListInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 * 날짜 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateTimeFormat 동일 포맷).
 */
@Mapper(componentModel = "spring")
public interface AdminUserToolConverter extends ToolDateTimeSupport {

    List<AdminUserListToolResponse.AdminUserItem> toListItems(List<AdminUserListInfo> infos);

    default AdminUserListToolResponse toListResponse(CursorPageInfo<AdminUserListInfo> pageInfo) {
        return new AdminUserListToolResponse(toListItems(pageInfo.list()), pageInfo.pageSize(),
                pageInfo.nextCursor(), toCursorValue(pageInfo.nextCursorValue()), pageInfo.nextCursorType(), pageInfo.hasNext());
    }

    AdminUserStorageUsageToolResponse toStorageUsageResponse(AdminUserStorageUsageInfo info);

    AdminUserDetailToolResponse toDetailResponse(AdminUserDetailInfo info);

    // 웹 감사 로그 목록(AuditLogController.list) 동형 — 항목 필드 이름이 AuditLogItemResponse와 전부 같아 자동 매핑, occurredAt(Instant)만 ToolDateTimeSupport로 String
    List<AdminAuditLogListToolResponse.AuditLogItem> toAuditLogItems(List<AuditLogItemResponse> items);

    default AdminAuditLogListToolResponse toAuditLogListResponse(PaginationResponse<AuditLogItemResponse> response) {
        ApiPage page = response.getPage();
        return new AdminAuditLogListToolResponse(toAuditLogItems(response.getList()),
                page.getTotalCount(), page.getPageIndex(), page.getPageSize());
    }

    List<AdminUserSearchToolResponse.AdminUserSearchItem> toSearchItems(List<AdminUserSearchInfo> infos);

    default AdminUserSearchToolResponse toSearchResponse(List<AdminUserSearchInfo> infos) {
        return new AdminUserSearchToolResponse(toSearchItems(infos));
    }

    // 웹 AdminUserResponseConverter.toBulkStatusResponse 동형 — 배열 그대로(래퍼 없음), 3필드 이름 동일이라 자동 매핑
    List<AdminUserBulkStatusToolResponse> toBulkStatusResponse(List<AdminUserBulkStatusInfo> infos);

    // 웹 AdminUserResponseConverter.toBulkUpsertResponse 동형(3필드·중첩 InsertedUser userId·userSeq).
    // 수동 매핑인 이유: 필드가 primitive int라 MapStruct가 int→int 변환에 ToolDateTimeSupport.toCursorValue(Object)를 골라 생성 코드가 컴파일되지 않는다.
    default AdminUserBulkUpsertToolResponse toBulkUpsertResponse(AdminUserBulkUpsertInfo info) {
        List<AdminUserBulkUpsertToolResponse.InsertedUser> insertedUsers = info.insertedUsers() == null ? null
                : info.insertedUsers().stream()
                        .map(u -> new AdminUserBulkUpsertToolResponse.InsertedUser(u.userId(), u.userSeq()))
                        .toList();
        return new AdminUserBulkUpsertToolResponse(info.insertedCount(), info.updatedCount(), insertedUsers);
    }

    // Instant 2필드(pendingDeleteDate·deleteScheduleTime)는 ToolDateTimeSupport.toDateTimeString(Instant)이 자동 적용된다
    List<AdminPendingDeleteUserListToolResponse.PendingDeleteUserItem> toPendingDeleteItems(List<AdminPendingDeleteUserListInfo> infos);

    default AdminPendingDeleteUserListToolResponse toPendingDeleteListResponse(CursorPageInfo<AdminPendingDeleteUserListInfo> pageInfo) {
        return new AdminPendingDeleteUserListToolResponse(toPendingDeleteItems(pageInfo.list()), pageInfo.pageSize(),
                pageInfo.nextCursor(), toCursorValue(pageInfo.nextCursorValue()), pageInfo.nextCursorType(), pageInfo.hasNext());
    }

}
