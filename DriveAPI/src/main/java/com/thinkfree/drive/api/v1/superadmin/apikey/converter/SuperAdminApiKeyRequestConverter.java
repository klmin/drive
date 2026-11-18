package com.thinkfree.drive.api.v1.superadmin.apikey.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.application.apikey.command.ApiKeyExpireDateUpdateCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = DriveTime.class)
public interface SuperAdminApiKeyRequestConverter {



    /** 조회 범위는 ApiKeyScope 로 따로 넘긴다 */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "registerUserSeq", source = "registerUserSeq")
    @Mapping(target = "cursorValue", source = "pagingRequest.cv")
    @Mapping(target = "cursorId",    source = "pagingRequest.ci")
    @Mapping(target = "cursorType",  source = "pagingRequest.ct")
    @Mapping(target = "pageSize",    source = "pagingRequest.ps")
    ApiKeyListCommand toListCommand(CursorPagingRequest pagingRequest, Integer tenantSeq, Integer registerUserSeq);

    /** 로그인 사용자 정보는 ApiKeyActor 로 따로 넘긴다 */
    @Mapping(target = "targetUserSeq", source = "userSeq")
    @Mapping(target = "expireDate", expression = "java(DriveTime.endOfDay(request.expireDate()))")
    @Mapping(target = "isProtected", expression = "java(Boolean.TRUE.equals(request.pt()))")
    ApiKeyIssueCommand toIssueCommand(SuperAdminApiKeyCreateRequest request);

    /** 경로 변수(apiKeySeq)와 요청 본문(expireDate)을 하나의 Command 로 합친다 */
    @Mapping(target = "expireDate", expression = "java(DriveTime.endOfDay(request.expireDate()))")
    ApiKeyExpireDateUpdateCommand toExpireDateUpdateCommand(Integer apiKeySeq, SuperAdminApiKeyExpireDateUpdateRequest request);
}
