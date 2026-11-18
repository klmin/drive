package com.thinkfree.drive.sso.protocol.converter;

import com.thinkfree.drive.sso.protocol.jwt.query.SsoJwtConfigQuery;
import com.thinkfree.drive.sso.protocol.oidc.query.SsoOidcConfigQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SsoProtocolConverter {
    SsoOidcConfigQuery.Find toSsoOidcFindQuery(Integer ssoProviderSeq);
    SsoJwtConfigQuery.Find toSsoJwtFindQuery(Integer ssoProviderSeq);
}
