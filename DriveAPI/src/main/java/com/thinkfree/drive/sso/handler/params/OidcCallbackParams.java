package com.thinkfree.drive.sso.handler.params;

public record OidcCallbackParams(
        String code,
        String redirectUri
)
        implements SsoCallbackParams
{}
