package com.thinkfree.drive.sso.protocol.factory;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.converter.SsoProtocolConverter;
import com.thinkfree.drive.sso.protocol.exception.SsoProtocolErrorCode;
import com.thinkfree.drive.sso.protocol.query.SsoProtocolFindQuery;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SsoProtocolQueryFactory {
    
    private final SsoProtocolConverter ssoProtocolConverter;
    
    public SsoProtocolFindQuery createFindQuery(int ssoProviderSeq, SsoProviderProtocolType ssoProviderProtocolType){
        return switch (ssoProviderProtocolType){
            case OIDC -> ssoProtocolConverter.toSsoOidcFindQuery(ssoProviderSeq);
            case JWT -> ssoProtocolConverter.toSsoJwtFindQuery(ssoProviderSeq);
            case SAML -> throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL);
        };
    }
}
