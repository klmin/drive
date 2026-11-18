package com.thinkfree.drive.sso.handler.params;

public sealed interface SsoCallbackParams permits OidcCallbackParams, SamlCallbackParams {}
