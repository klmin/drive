package com.thinkfree.drive.sso.protocol.jwt.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum KeySourceType {
    
    PUBLIC_KEY("public_key"),
    JWKS("jwks")
    ;
    
    @JsonValue
    private final String value;
    
    @JsonCreator
    public static KeySourceType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
