package com.thinkfree.drive.api.v1.admin.user.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserBulkStatusResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserBulkUpsertResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserListResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserLogResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserSearchResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserStorageUsageResponse;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkStatusInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkUpsertInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserListInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserLogInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserSearchInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserStorageUsageInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data → Info 조립은 application의 AdminUserInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminUserResponseConverter {

    List<AdminUserListResponse> toListResponse(List<AdminUserListInfo> infos);

    List<AdminUserBulkStatusResponse> toBulkStatusResponse(List<AdminUserBulkStatusInfo> infos);

    AdminUserStorageUsageResponse toAdminUserStorageUsageResponse(AdminUserStorageUsageInfo info);

    AdminUserBulkUpsertResponse toBulkUpsertResponse(AdminUserBulkUpsertInfo info);

    List<AdminUserLogResponse> toAdminUserLogResponse(List<AdminUserLogInfo> infos);

    List<AdminUserSearchResponse> toSearchResponse(List<AdminUserSearchInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

}
