package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ResourceSortFieldConverter implements Converter<String, ResourceSortField> {
    @Override
    public ResourceSortField convert(String source) {
        return ResourceSortField.from(source);
    }
}
