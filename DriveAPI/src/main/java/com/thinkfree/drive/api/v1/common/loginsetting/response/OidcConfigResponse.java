package com.thinkfree.drive.api.v1.common.loginsetting.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;

import java.time.Instant;

public record OidcConfigResponse
(
    int ssoProviderSeq,
    String displayName,
    SsoProviderStatus status,
    String issuerUrl,
    String clientId,
    String clientSecret,
    String scopes,
    Boolean allowIdpInitiated,
    @DriveDateTimeFormat
    Instant registerDate
)
    implements SsoProtocolConfigResponse
{
}
