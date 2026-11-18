package com.thinkfree.drive.auth.model;


import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;

public record SsoSignupClaims
(
        SsoProviderProtocolType protocolType,
        int tenantSeq,
        int ssoProviderSeq,
        String subject,
        String email,
        String name
)
{}
