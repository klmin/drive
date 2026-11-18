package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.trash.enums.TrashSortField;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TrashSortFieldConverter implements Converter<String, TrashSortField> {
    @Override
    public TrashSortField convert(String source) {
        return TrashSortField.from(source);
    }
}
