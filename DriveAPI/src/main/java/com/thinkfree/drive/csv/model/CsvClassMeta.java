package com.thinkfree.drive.csv.model;

import java.util.List;
import java.util.Map;

public record CsvClassMeta
(
        Class<?> clazz,
        List<CsvFieldMeta> fields,
        Map<String, CsvFieldMeta> byFieldName,
        Map<String, CsvFieldMeta> byKey,
        List<CsvRequiredTogetherRule> requiredTogetherRules
)
{
}
