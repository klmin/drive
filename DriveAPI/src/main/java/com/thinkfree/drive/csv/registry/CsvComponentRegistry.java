package com.thinkfree.drive.csv.registry;

import com.thinkfree.drive.csv.model.CsvClassMeta;
import com.thinkfree.drive.csv.validation.CsvConstraint;
import com.thinkfree.drive.csv.validation.CsvNormalizer;
import com.thinkfree.drive.csv.validation.CsvTypeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CsvComponentRegistry {
    
    private final CsvMetaRegistry csvMetaRegistry;
    private final CsvConstraintRegistry constraintRegistry;
    private final CsvNormalizerRegistry csvNormalizerRegistry;
    private final CsvTypeHandlerRegistry csvTypeHandlerRegistry;
    
    public CsvClassMeta metaOf(Class<?> clazz) {
        return csvMetaRegistry.get(clazz);
    }
    
    public CsvConstraint constraint(Class<? extends CsvConstraint> type) {
        return constraintRegistry.get(type);
    }
    
    public CsvNormalizer normalizer(Class<? extends CsvNormalizer> type) {
        return csvNormalizerRegistry.get(type);
    }
    
    public <T> CsvTypeHandler<T> typeHandler(Class<T> type) {
        return csvTypeHandlerRegistry.get(type);
    }
    
}
