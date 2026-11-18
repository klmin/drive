package com.thinkfree.drive.user.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MfaStatus {
    DISABLED("미사용"),
    PENDING("등록중"),
    ENABLED("사용");
    
    private final String name;
    
    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }
}
