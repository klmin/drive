package com.thinkfree.drive.csv.validation;

import com.thinkfree.drive.csv.model.CsvFieldMeta;

public interface CsvTypeHandler<T> {
    T convert(String value);
    
    void validate(T value, CsvFieldMeta meta);
    
    default T convertAndValidate(String normalized, CsvFieldMeta meta) {
        T v = convert(normalized);
        validate(v, meta);
        return v;
    }
}
