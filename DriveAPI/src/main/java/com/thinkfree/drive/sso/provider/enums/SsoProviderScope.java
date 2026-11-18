package com.thinkfree.drive.sso.provider.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SsoProviderScope {
    TENANT,
    GLOBAL
    ;

    @JsonCreator
    public static SsoProviderScope from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
