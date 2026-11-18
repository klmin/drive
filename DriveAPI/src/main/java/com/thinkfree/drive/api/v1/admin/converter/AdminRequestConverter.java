package com.thinkfree.drive.api.v1.admin.converter;

import com.thinkfree.drive.api.v1.admin.request.AdminImportCsvRequest;
import com.thinkfree.drive.api.v1.admin.request.AdminUserUpdateRequest;
import com.thinkfree.drive.api.v1.admin.request.UserRegisterRequest;
import com.thinkfree.drive.application.admin.command.AdminImportCsvCommand;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 query/command 생성은 application의 AdminCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminRequestConverter {

    @Mapping(target = "userId",           source = "ui")
    @Mapping(target = "encPwd",           source = "encPwd")
    @Mapping(target = "userName",         source = "un")
    @Mapping(target = "totalQuotaByte",   source = "tq")
    @Mapping(target = "roles",            source = "roles")
    @Mapping(target = "profileImageFile", source = "pif")
    @Mapping(target = "countryCode",      source = "ct")
    @Mapping(target = "phoneNumber",      source = "pn")
    AdminRegisterUserCommand toRegisterCommand(UserRegisterRequest request);

    @Mapping(target = "userName",           source = "un")
    @Mapping(target = "totalQuotaByte",     source = "tq")
    @Mapping(target = "countryCode",        source = "ct")
    @Mapping(target = "phoneNumber",        source = "pn")
    @Mapping(target = "profileImageFile",   source = "pif")
    @Mapping(target = "deleteProfileImage", source = "dpi")
    @Mapping(target = "roles",              source = "roles")
    AdminUpdateUserCommand toUpdateCommand(AdminUserUpdateRequest request);

    @Mapping(target = "file",      source = "file")
    @Mapping(target = "sendEmail", source = "se")
    AdminImportCsvCommand toImportCsvCommand(AdminImportCsvRequest request);

}
