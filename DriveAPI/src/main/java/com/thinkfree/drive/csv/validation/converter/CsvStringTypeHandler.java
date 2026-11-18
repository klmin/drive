package com.thinkfree.drive.csv.validation.converter;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.validation.CsvTypeHandler;
import com.thinkfree.drive.util.StringUtil;
import org.springframework.stereotype.Component;

@Component
public class CsvStringTypeHandler implements CsvTypeHandler<String> {
    
    @Override
    public String convert(String value) {
        return value;
    }
    
    @Override
    public void validate(String value, CsvFieldMeta meta) {
        var min = meta.csvField().min();
        var max = meta.csvField().max();
        StringUtil.requireRange(value, min, max, () -> CsvException.error(CsvErrorCode.INVALID_VALUE, value));
    }
}