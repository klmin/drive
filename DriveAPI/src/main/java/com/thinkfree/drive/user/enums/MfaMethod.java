package com.thinkfree.drive.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MfaMethod {
    TOTP,
    EMAIL_CODE;

    @JsonCreator
    public static MfaMethod from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
