package com.thinkfree.drive.sso.protocol.oidc.converter;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.oidc.dto.SsoOidcConfigDto;
import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OidcConfigConverter {
    
    SsoOidcConfig toUpsertOidcConfig(SsoOidcConfigDto.Upsert dto, int ssoProviderSeq);

    SsoLoginConfigData toSsoLoginConfigData(Integer ssoProviderSeq, String errorRedirectUrl);
}
