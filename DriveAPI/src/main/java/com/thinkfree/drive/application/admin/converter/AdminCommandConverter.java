package com.thinkfree.drive.application.admin.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import com.thinkfree.drive.application.admin.csv.AdminUserCsvImportRow;
import com.thinkfree.drive.storage.enums.StorageUnit;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.dto.AdminUserUpdateDto;
import com.thinkfree.drive.user.enums.RoleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * application Command → 도메인 dto/query 변환 전담. 웹 요청 → Command 변환은 api.v1 AdminRequestConverter가 담당한다.
 * 비밀번호는 원문 그대로 넘긴다 — 인코딩은 비밀번호 변경류와 동일하게 도메인 service가 수행.
 */
@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class, StorageUnit.class})
public interface AdminCommandConverter {

    @Mapping(target = "userId", source = "command.userId")
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "encPwd", source = "command.encPwd")
    @Mapping(target = "userName", source = "command.userName")
    @Mapping(target = "totalQuotaByte", source = "command.totalQuotaByte")
    @Mapping(target = "registerUserSeq", source = "loginUserSeq")
    @Mapping(target = "roles", source = "command.roles")
    @Mapping(target = "countryCode", source = "command.countryCode")
    @Mapping(target = "phoneNumber", source = "command.phoneNumber")
    AdminUserSaveDto toSaveDto(AdminRegisterUserCommand command, int loginUserSeq, int tenantSeq);

    AdminUserUpdateDto toUpdateDto(AdminUpdateUserCommand command);

    @Mapping(target="roles", expression = "java(java.util.Set.of(com.thinkfree.drive.user.enums.RoleType.USER))") // CSV 가져오기는 USER 고정
    @Mapping(target="encPwd", source = "row.pw")
    @Mapping(target="registerUserSeq", source = "registerUserSeq")
    @Mapping(target="tenantSeq", source = "tenantSeq")
    @Mapping(target="totalQuotaByte", expression = "java(StorageUnit.GB.toBytes(row.getTotalQuotaByte()))")
    AdminUserSaveDto toCsvSaveDto(AdminUserCsvImportRow row, int registerUserSeq, int tenantSeq);

    default List<AdminUserSaveDto> toCsvSaveDtos(List<AdminUserCsvImportRow> rows, int registerUserSeq, int tenantSeq) {
        return rows.stream().map(row -> toCsvSaveDto(row, registerUserSeq, tenantSeq)).toList();
    }


}
