package com.thinkfree.drive.api.v1.admin.user.pendingdelete.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.response.AdminPendingDeleteUserListResponse;
import com.thinkfree.drive.application.admin.user.pendingdelete.info.AdminPendingDeleteUserListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 data → Info 조립은 application의 AdminPendingDeleteUserInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminPendingDeleteUserResponseConverter {

    List<AdminPendingDeleteUserListResponse> toListResponse(List<AdminPendingDeleteUserListInfo> infos);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }
}
