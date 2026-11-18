package com.thinkfree.drive.application.admin.user.pendingdelete.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeleteUserListCommand;
import com.thinkfree.drive.user.query.UserPendingDeleteListQuery;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * application Command → 도메인 query 변환 전담. 웹 요청 → Command 변환은 api.v1 AdminPendingDeleteUserRequestConverter가 담당한다.
 * 날짜 경계(시작일 자정 이상 ~ 종료일 다음 날 자정 미만) 변환을 여기서 수행한다 (구 api.v1 컨버터와 동일 정책).
 */
@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class, DriveTime.class})
public interface AdminPendingDeleteUserCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "pendingDeleteStartDate", expression = "java(DriveTime.startOfDay(command.pendingDeleteStartDate()))")
    @Mapping(target = "pendingDeleteEndDate", expression = "java(DriveTime.endExclusive(command.pendingDeleteEndDate()))")
    @Mapping(target = "deleteScheduleStartDate", expression = "java(DriveTime.startOfDay(command.deleteScheduleStartDate()))")
    @Mapping(target = "deleteScheduleEndDate", expression = "java(DriveTime.endExclusive(command.deleteScheduleEndDate()))")
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    UserPendingDeleteListQuery toListQuery(AdminPendingDeleteUserListCommand command, Integer tenantSeq);
}
