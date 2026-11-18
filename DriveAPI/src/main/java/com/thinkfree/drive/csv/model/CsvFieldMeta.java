package com.thinkfree.drive.csv.model;

import com.thinkfree.drive.csv.annotation.CsvField;

import java.lang.reflect.Field;

public record CsvFieldMeta
(
    Field field,
    String key,
    int index,
    Class<?> type,
    CsvField csvField
)
{
}
