package com.thinkfree.drive.sso.handler.params;

public record SamlCallbackParams(
        String samlResponse,
        String relayState
)
        implements SsoCallbackParams
{}
