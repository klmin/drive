package com.thinkfree.drive.sso.provider.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SsoProviderStatus {
    ACTIVE,
    INACTIVE
    ;
    
    @JsonCreator
    public static SsoProviderStatus from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
