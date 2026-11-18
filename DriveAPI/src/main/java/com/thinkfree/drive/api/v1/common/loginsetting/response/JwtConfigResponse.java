package com.thinkfree.drive.api.v1.common.loginsetting.response;

import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;

public record JwtConfigResponse
(
    int ssoProviderSeq,
    String displayName,
    SsoProviderStatus status,
    String issuer,
    String audience,
    KeySourceType keySourceType,
    String publicKey,
    String jwksUri,
    String emailClaimName,
    String errorRedirectUrl
)
    implements SsoProtocolConfigResponse
{
}
