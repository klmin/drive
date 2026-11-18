package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.common.search.enums.SortOrder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SortOrderConverter implements Converter<String, SortOrder> {
    @Override
    public SortOrder convert(String source) {
        return SortOrder.from(source);
    }
}
