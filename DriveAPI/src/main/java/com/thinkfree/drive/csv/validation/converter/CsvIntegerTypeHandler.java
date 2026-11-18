package com.thinkfree.drive.csv.validation.converter;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.validation.CsvTypeHandler;
import com.thinkfree.drive.util.NumberUtils;
import org.springframework.stereotype.Component;

@Component
public class CsvIntegerTypeHandler implements CsvTypeHandler<Integer> {
    
    @Override
    public Integer convert(String value) {
        return NumberUtils.parseIntOrThrow(value, () -> CsvException.error(CsvErrorCode.INVALID_VALUE, value));
    }
    @Override
    public void validate(Integer value, CsvFieldMeta meta) {
        var min = meta.csvField().min();
        var max = meta.csvField().max();
        NumberUtils.requireRange(value, min, max, () -> CsvException.error(CsvErrorCode.INVALID_VALUE, value.toString()));
    }
}
