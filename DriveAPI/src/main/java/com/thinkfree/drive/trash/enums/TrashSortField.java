package com.thinkfree.drive.trash.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.thinkfree.drive.common.search.support.SortFieldHolder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TrashSortField {
    NAME("resource_name", SortFieldHolder::getResourceName),
    SIZE("size_byte", SortFieldHolder::getSizeCursorValue),
    REGISTER("register_date", SortFieldHolder::getRegisterDate),
    ;
    
    private final String column;
    private final Function<SortFieldHolder, Comparable<?>> extractor;

    @JsonCreator
    public static TrashSortField from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}
