package com.thinkfree.drive.apikey.converter;

import com.thinkfree.drive.apikey.dto.ApiKeyIssueDto;
import com.thinkfree.drive.apikey.dto.ApiKeySaveDto;
import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {ApiKeyStatus.class})
public interface ApiKeyConverter {

    /** 발급 요청 + 검증 결과 → 저장 확정값 */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "userSeq", source = "target.userSeq")
    /** keyRole = 발급 경로·대상 보유 역할로 정한 키 역할(ApiKeyIssuer.resolveKeyRole) — 대상 계정의 tb_user.role_type 이 아니다 */
    @Mapping(target = "roleType", source = "keyRole")
    @Mapping(target = "scope", source = "scope")
    @Mapping(target = "isProtected", expression = "java(Boolean.TRUE.equals(dto.getIsProtected()))")
    ApiKeySaveDto toApiKeySaveDto(ApiKeyIssueDto dto, Integer tenantSeq, UserDetailData target, String scope, RoleType keyRole);

    /** registerUserSeq·registerDate 는 insert 때 Spring Data Auditing 이 채운다 */
    @Mapping(target = "apiKey", source = "rawApiKey")
    @Mapping(target = "status", expression = "java(ApiKeyStatus.ACTIVE)")
    ApiKey toSaveEntity(ApiKeySaveDto dto, String rawApiKey);
}
