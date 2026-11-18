package com.thinkfree.drive.resource.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.thinkfree.drive.common.search.support.SortFieldHolder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ResourceSortField {

    NAME("resource_name", SortFieldHolder::getResourceName),
    SIZE("size_byte", SortFieldHolder::getSizeCursorValue),
    UPDATE("update_date", SortFieldHolder::getUpdateDate),
    OPEN("update_date", SortFieldHolder::getRecentUpdateDate);

    private final String column;
    private final Function<SortFieldHolder, Comparable<?>> extractor;

    @JsonCreator
    public static ResourceSortField from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
}