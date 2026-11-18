package com.thinkfree.drive.sso.protocol.jwt.converter;

import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.protocol.jwt.dto.SsoJwtConfigDto;
import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JwtConfigConverter {
    SsoJwtConfig toUpsertJwtConfig(SsoJwtConfigDto.Upsert dto, int ssoProviderSeq);

    SsoLoginConfigData toSsoLoginConfigData(Integer ssoProviderSeq, String errorRedirectUrl);
}
