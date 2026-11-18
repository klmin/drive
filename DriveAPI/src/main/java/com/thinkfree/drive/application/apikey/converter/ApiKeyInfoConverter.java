package com.thinkfree.drive.application.apikey.converter;

import com.thinkfree.drive.application.apikey.info.ApiKeyListInfo;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.support.ApiKeyResponseSupport;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 도메인 data → Info 조립 전담. Info → 각 진입점 응답 변환은 어댑터의 ResponseConverter 가 담당한다.
 */
@Mapper(componentModel = "spring", imports = {ApiKeyResponseSupport.class})
public interface ApiKeyInfoConverter {

    /** @param hideKey true 면 키 값을 화면 블러용 값으로 내려준다(SYSTEM_MANAGED + SINGLE) */
    @Mapping(target = "maskedApiKey", expression = "java(ApiKeyResponseSupport.maskApiKey(apiKeyData.getApiKey(), hideKey))")
    @Mapping(target = "scopes", expression = "java(ApiKeyResponseSupport.toScopes(apiKeyData.getScope()))")
    ApiKeyListInfo toListInfo(ApiKeyData apiKeyData, @Context boolean hideKey);

    List<ApiKeyListInfo> toListInfos(List<ApiKeyData> apiKeyDataList, @Context boolean hideKey);
}
