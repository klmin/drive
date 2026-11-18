package com.thinkfree.drive.api.v1.admin.user.pendingdelete.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeletePermanentRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeleteUserListRequest;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeletePermanentCommand;
import com.thinkfree.drive.application.admin.user.pendingdelete.command.AdminPendingDeleteUserListCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query 생성은 application의 AdminPendingDeleteUserCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminPendingDeleteUserRequestConverter {

    @Mapping(target = "pendingDeleteStartDate",   source = "request.pdSd")
    @Mapping(target = "pendingDeleteEndDate",     source = "request.pdEd")
    @Mapping(target = "deleteScheduleStartDate",  source = "request.dsSd")
    @Mapping(target = "deleteScheduleEndDate",    source = "request.dsEd")
    @Mapping(target = "cursorValue",              source = "pagingRequest.cv")
    @Mapping(target = "cursorId",                 source = "pagingRequest.ci")
    @Mapping(target = "cursorType",               source = "pagingRequest.ct")
    @Mapping(target = "pageSize",                 source = "pagingRequest.ps")
    AdminPendingDeleteUserListCommand toListCommand(AdminPendingDeleteUserListRequest request, CursorPagingRequest pagingRequest);

    @Mapping(target = "userSeqList", source = "ul")
    AdminPendingDeletePermanentCommand toPermanentCommand(AdminPendingDeletePermanentRequest request);
}
