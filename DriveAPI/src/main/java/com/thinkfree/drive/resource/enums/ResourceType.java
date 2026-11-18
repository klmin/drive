package com.thinkfree.drive.resource.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ResourceType {
    FILE,
    FOLDER;

    public boolean isFile(){
        return ResourceType.FILE == this;
    }

    @JsonCreator
    public static ResourceType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
