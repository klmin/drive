package com.thinkfree.drive.api.v1.admin.user.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkUpsertRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkUpsertRequest.UserItem;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserListRequest;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserListCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserLogListCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query/command 생성은 application의 AdminUserCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminUserRequestConverter {

    @Mapping(target = "status",      source = "request.s")
    @Mapping(target = "userId",      source = "request.u")
    @Mapping(target = "roles",       source = "request.rt")
    @Mapping(target = "cursorValue", source = "pagingRequest.cv")
    @Mapping(target = "cursorId",    source = "pagingRequest.ci")
    @Mapping(target = "cursorType",  source = "pagingRequest.ct")
    @Mapping(target = "pageSize",    source = "pagingRequest.ps")
    AdminUserListCommand toListCommand(AdminUserListRequest request, CursorPagingRequest pagingRequest);

    @Mapping(target = "cursorValue", source = "cv")
    @Mapping(target = "cursorId",    source = "ci")
    @Mapping(target = "cursorType",  source = "ct")
    @Mapping(target = "pageSize",    source = "ps")
    AdminUserLogListCommand toLogListCommand(CursorPagingRequest pagingRequest);

    AdminUserBulkUpsertCommand toBulkUpsertCommand(AdminUserBulkUpsertRequest request);

    @Mapping(target = "userSeq",        source = "us")
    @Mapping(target = "userId",         source = "ui")
    @Mapping(target = "encPwd",         source = "encPwd")
    @Mapping(target = "userName",       source = "un")
    @Mapping(target = "totalQuotaByte", source = "tq")
    @Mapping(target = "roles",          source = "roles")
    @Mapping(target = "countryCode",    source = "ct")
    @Mapping(target = "phoneNumber",    source = "pn")
    UserItemCommand toUserItemCommand(UserItem item);

}
