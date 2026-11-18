package com.thinkfree.drive.api.v1.auth.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum LoginType {
    LOCAL("local"),
    OIDC("oidc"),
    JWT("jwt")
    ;
    
    @JsonValue
    private final String value;
    
    @JsonCreator
    public static LoginType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
    
}
