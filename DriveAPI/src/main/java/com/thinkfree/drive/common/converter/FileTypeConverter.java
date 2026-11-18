package com.thinkfree.drive.common.converter;

import com.thinkfree.drive.common.search.enums.FileType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class FileTypeConverter implements Converter<String, FileType> {
    @Override
    public FileType convert(String source) {
        return FileType.from(source);
    }
}
