package com.thinkfree.drive.mfa.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MfaChallengeType {
    SETUP("setup"),
    VERIFY("verify")
    ;
    
    @JsonValue
    private final String value;
}
