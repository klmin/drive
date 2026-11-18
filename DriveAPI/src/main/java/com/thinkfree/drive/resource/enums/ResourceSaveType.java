package com.thinkfree.drive.resource.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ResourceSaveType {
    VERSION, DUPLICATE, OVERWRITE;
    
    @JsonCreator
    public static ResourceSaveType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
    
    public boolean isDuplicate(){
        return ResourceSaveType.DUPLICATE == this;
    }
    public boolean isVersion(){
        return ResourceSaveType.VERSION == this;
    }
    public boolean isOverwrite(){
        return ResourceSaveType.OVERWRITE == this;
    }
}
