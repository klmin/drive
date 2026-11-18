package com.thinkfree.drive.resource.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum NameConflictPolicy {
    NONE, AUTO;
    
    @JsonCreator
    public static NameConflictPolicy from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
