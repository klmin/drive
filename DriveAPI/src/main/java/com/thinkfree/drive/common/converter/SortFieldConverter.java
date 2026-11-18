package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.common.search.enums.SortField;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SortFieldConverter implements Converter<String, SortField> {
    @Override
    public SortField convert(String source) {
        return SortField.from(source);
    }
}
