package com.thinkfree.drive.api.v1.user.apikey.converter;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.application.apikey.command.ApiKeyExpireDateUpdateCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.global.time.DriveTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = DriveTime.class)
public interface UserApiKeyRequestConverter {



    /** 조회 범위는 ApiKeyScope 로 따로 넘긴다 */
    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "registerUserSeq", ignore = true)
    @Mapping(target = "cursorValue", source = "cv")
    @Mapping(target = "cursorId",    source = "ci")
    @Mapping(target = "cursorType",  source = "ct")
    @Mapping(target = "pageSize",    source = "ps")
    ApiKeyListCommand toListCommand(CursorPagingRequest pagingRequest);

    /** 로그인 사용자 정보는 ApiKeyActor 로 따로 넘긴다 */
    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "targetUserSeq", ignore = true)
    @Mapping(target = "expireDate", expression = "java(DriveTime.endOfDay(request.expireDate()))")
    @Mapping(target = "roleType", ignore = true) // 키 역할은 경로로 정해진다(슈퍼관리자 경로만 지정)
    ApiKeyIssueCommand toIssueCommand(UserApiKeyCreateRequest request);

    /** 경로 변수(apiKeySeq)와 요청 본문(expireDate)을 하나의 Command 로 합친다 */
    @Mapping(target = "expireDate", expression = "java(DriveTime.endOfDay(request.expireDate()))")
    ApiKeyExpireDateUpdateCommand toExpireDateUpdateCommand(Integer apiKeySeq, UserApiKeyExpireDateUpdateRequest request);
}
