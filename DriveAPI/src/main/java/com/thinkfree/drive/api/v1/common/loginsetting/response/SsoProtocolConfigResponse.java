package com.thinkfree.drive.api.v1.common.loginsetting.response;

public sealed interface SsoProtocolConfigResponse
        permits OidcConfigResponse, JwtConfigResponse {}
