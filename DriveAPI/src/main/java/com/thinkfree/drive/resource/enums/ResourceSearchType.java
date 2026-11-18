package com.thinkfree.drive.resource.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ResourceSearchType {
    MY, ALL;

    @JsonCreator
    public static ResourceSearchType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
