package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.resource.enums.ResourceSaveType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ResourceSaveTypeConverter implements Converter<String, ResourceSaveType> {
    @Override
    public ResourceSaveType convert(String source) {
        return ResourceSaveType.from(source);
    }
}
