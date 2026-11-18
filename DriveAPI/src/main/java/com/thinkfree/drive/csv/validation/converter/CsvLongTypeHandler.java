package com.thinkfree.drive.csv.validation.converter;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.validation.CsvTypeHandler;
import com.thinkfree.drive.util.NumberUtils;
import org.springframework.stereotype.Component;

@Component
public class CsvLongTypeHandler implements CsvTypeHandler<Long> {
    
    @Override
    public Long convert(String value) {
        return NumberUtils.parseLongOrThrow(value, () -> CsvException.error(CsvErrorCode.INVALID_VALUE, value));
    }
    
    @Override
    public void validate(Long value, CsvFieldMeta meta) {
        var min = meta.csvField().min();
        var max = meta.csvField().max();
        NumberUtils.requireRange(value, min, max, () -> CsvException.error(CsvErrorCode.INVALID_VALUE, value.toString()));
    }
    
}
