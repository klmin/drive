package com.thinkfree.drive.sso.provider.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SsoProviderProtocolType {
    OIDC,
    SAML,
    JWT
    ;

    @JsonCreator
    public static SsoProviderProtocolType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }

    public boolean isSingleActiveType() {
        return this == OIDC || this == SAML || this == JWT;
    }
    
}
