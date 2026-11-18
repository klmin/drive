package com.thinkfree.drive.api.page;

import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class CursorApiPage<CV> {
    private final int  pageSize;
    private final CV    nextCursorValue;
    private final Number   nextCursor;
    private final ResourceType nextCursorType;
    private final boolean hasNext;

    public static <T, CV> CursorApiPage<?> of(List<T> items, int pageSize, Function<T, CV> valueExtractor, Function<T, Number> idExtractor, Function<T, ResourceType> typeExtractor) {
        boolean hasNext = items.size() == pageSize;

        T lastItem = hasNext ? items.get(items.size() - 1) : null;
        Number nextCursor = (hasNext && idExtractor != null) ? idExtractor.apply(lastItem) : null;
        CV nextCursorValue = (hasNext && valueExtractor != null) ? valueExtractor.apply(lastItem) : null;
        ResourceType nextCursorType = (hasNext && typeExtractor != null) ? typeExtractor.apply(lastItem) : null;

        return new CursorApiPage<>(pageSize, nextCursorValue, nextCursor, nextCursorType, hasNext);
    }

    public static <CV> CursorApiPage<CV> of(int pageSize, CV nextCursorValue, Number nextCursor, ResourceType nextCursorType, boolean hasNext) {
        return new CursorApiPage<>(pageSize, nextCursorValue, nextCursor, nextCursorType, hasNext);
    }
}


