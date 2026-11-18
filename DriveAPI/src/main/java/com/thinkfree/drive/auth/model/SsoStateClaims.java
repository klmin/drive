package com.thinkfree.drive.auth.model;


public record SsoStateClaims
(
        Integer ssoProviderSeq,
        Integer tenantSeq
)
{}
