package com.thinkfree.drive.api.v1.superadmin.loginsetting.converter;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.request.JwtConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.OidcConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.ProviderItemRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.response.JwtConfigResponse;
import com.thinkfree.drive.api.v1.common.loginsetting.response.OidcConfigResponse;
import com.thinkfree.drive.api.v1.common.loginsetting.response.SsoProtocolConfigResponse;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingSaveRequest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.superadmin.loginsetting.dto.SuperAdminLoginSettingUpsertDto;
import com.thinkfree.drive.sso.protocol.exception.SsoProtocolErrorCode;
import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.dto.SsoJwtConfigDto;
import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.protocol.oidc.dto.SsoOidcConfigDto;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.dto.SsoProviderUpsertDto;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.util.StringUtils;

import java.util.List;

@Mapper(componentModel = "spring", imports = {ApiRuntimeException.class, SsoProtocolErrorCode.class, SsoProviderProtocolType.class, SsoProviderScope.class})
public interface SuperAdminLoginSettingRequestConverter {

    default SsoProviderProtocolType toProtocolType(LoginType loginType) {
        return switch (loginType) {
            case OIDC -> SsoProviderProtocolType.OIDC;
            case JWT  -> SsoProviderProtocolType.JWT;
            default   -> throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL);
        };
    }

    @Mapping(target = "loginType", source = "l")
    @Mapping(target = "isEnforced", constant = "true")
    SuperAdminLoginSettingUpsertDto toEnforcedUpsertDto(SuperAdminLoginSettingSaveRequest request);

    default SsoProtocolConfigResponse toConfigResponse(SsoProviderDetailData detail) {
        var providerData = detail.getSsoProviderData();
        return switch (providerData.getProtocolType()) {
            case OIDC -> toOidcConfigResponse(providerData, (SsoOidcConfigData) detail.getSsoProtocolData());
            case JWT  -> toJwtConfigResponse(providerData, (SsoJwtConfigData) detail.getSsoProtocolData());
            case SAML -> throw ApiRuntimeException.type(SsoProtocolErrorCode.PROTOCOL_CONFIG_MISMATCH);
        };
    }

    @Mapping(target = "ssoProviderSeq", source = "providerData.ssoProviderSeq")
    OidcConfigResponse toOidcConfigResponse(SsoProviderData providerData, SsoOidcConfigData oidcData);

    @Mapping(target = "ssoProviderSeq", source = "providerData.ssoProviderSeq")
    JwtConfigResponse toJwtConfigResponse(SsoProviderData providerData, SsoJwtConfigData jwtData);

    default List<SsoProviderUpsertDto> toSsoProviderUpsertDtoList(List<ProviderItemRequest> items, SsoProviderProtocolType protocolType) {
        return items.stream()
                    .map(item -> toUpsertDto(item, protocolType))
                    .toList();
    }

    default SsoProviderUpsertDto toUpsertDto(ProviderItemRequest item, SsoProviderProtocolType protocolType) {
        var protocolUpsertDto = switch (protocolType) {
            case OIDC -> toOidcUpsertDto((OidcConfigRequest) item.config());
            case JWT  -> toJwtUpsertDto((JwtConfigRequest) item.config());
            case SAML -> throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL);
        };
        return toSsoProviderUpsertDto(item, protocolType, protocolUpsertDto);
    }

    @Mapping(target = "allowIdpInitiated", constant = "false")
    SsoOidcConfigDto.Upsert toOidcUpsertDto(OidcConfigRequest req);

    @Mapping(target = "publicKey", source = "publicKey", qualifiedByName = "stripPemHeaders")
    SsoJwtConfigDto.Upsert toJwtUpsertDto(JwtConfigRequest req);

    @Mapping(target = "tenantSeq", ignore = true)
    @Mapping(target = "scope", expression = "java(SsoProviderScope.GLOBAL)")
    @Mapping(target = "status", expression = "java(protocolType == SsoProviderProtocolType.JWT ? com.thinkfree.drive.sso.provider.enums.SsoProviderStatus.ACTIVE : item.status())")
    SsoProviderUpsertDto toSsoProviderUpsertDto(ProviderItemRequest item, SsoProviderProtocolType protocolType, SsoProtocolUpsertDto protocolUpsertDto);

    @Named("stripPemHeaders")
    default String stripPemHeaders(String pem) {
        if (!StringUtils.hasText(pem)){
            return null;
        }
        return pem.replace("-----BEGIN PUBLIC KEY-----", "")
                  .replace("-----END PUBLIC KEY-----", "")
                  .replaceAll("\\s", "");
    }
}
