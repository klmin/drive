package com.thinkfree.drive.application.admin.user.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkStatusCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserListCommand;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.query.UserBulkStatusQuery;
import com.thinkfree.drive.user.query.UserListQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * application Command → 도메인 query/command 변환 전담. 웹 요청 → Command 변환은 api.v1 AdminUserRequestConverter가 담당한다.
 */
@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class, RoleType.class})
public interface AdminUserCommandConverter {

    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    @Mapping(target = "status", source = "command.status")
    @Mapping(target = "userId", source = "command.userId")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    UserListQuery toListQuery(AdminUserListCommand command, List<RoleType> roles, Integer tenantSeq);

    @Mapping(target = "userIds", source = "command.userIds")
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    UserBulkStatusQuery toBulkStatusQuery(AdminUserBulkStatusCommand command, Integer tenantSeq);

    @Mapping(target = "userId", source = "item.userId")
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "encPwd", source = "item.encPwd")
    @Mapping(target = "userName", source = "item.userName")
    @Mapping(target = "totalQuotaByte", source = "item.totalQuotaByte")
    @Mapping(target = "registerUserSeq", source = "loginUserSeq")
    @Mapping(target = "roles", source = "item.roles")
    @Mapping(target = "countryCode", source = "item.countryCode")
    @Mapping(target = "phoneNumber", source = "item.phoneNumber")
    AdminUserSaveDto toSaveDto(UserItemCommand item, int loginUserSeq, int tenantSeq);

    default List<AdminUserSaveDto> toSaveDtos(List<UserItemCommand> items, int loginUserSeq, int tenantSeq) {
        return items.stream().map(item -> toSaveDto(item, loginUserSeq, tenantSeq)).toList();
    }

    AdminUserBulkUpdateDto toBulkUpdateDto(UserItemCommand item);

    List<AdminUserBulkUpdateDto> toBulkUpdateDtos(List<UserItemCommand> items);

}
