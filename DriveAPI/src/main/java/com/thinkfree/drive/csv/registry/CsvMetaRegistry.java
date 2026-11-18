package com.thinkfree.drive.csv.registry;

import com.thinkfree.drive.csv.annotation.CsvField;
import com.thinkfree.drive.csv.annotation.CsvHeader;
import com.thinkfree.drive.csv.annotation.CsvModel;
import com.thinkfree.drive.csv.annotation.CsvRequiredTogether;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvClassMeta;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.model.CsvRequiredTogetherRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvMetaRegistry {
    
    private final ConcurrentMap<Class<?>, CsvClassMeta> cache = new ConcurrentHashMap<>();

    public CsvClassMeta get(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, this::build);
    }
    
    private CsvClassMeta build(Class<?> clazz) {
        
        var fields = new ArrayList<CsvFieldMeta>();
        var byFieldName = new LinkedHashMap<String, CsvFieldMeta>();
        var byKey = new LinkedHashMap<String, CsvFieldMeta>();
        var className = clazz.getName();
        
        CsvModel model = clazz.getAnnotation(CsvModel.class);
        
        if (model == null) {
            log.error("[csv] @CsvModel missing. class : {}", className);
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }

        for (Field f : clazz.getDeclaredFields()) {
            
            var csvHeader = f.getAnnotation(CsvHeader.class);
            var fieldName = f.getName();
            
            if (csvHeader == null) {
                log.error("[csv] @CsvHeader missing. class : {}, field : {}", className, fieldName);
                throw CsvException.of(CsvErrorCode.HEADER_MISMATCH);
            }

            var key = csvHeader.key();
            var index = csvHeader.index();
            
            if (!StringUtils.hasText(key) || index < 0) {
                log.error("[csv] invalid annotation. class : {}, field : {}, key : {}, index : {}", className, fieldName, key, index);
                throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
            }

            var csvField = f.getAnnotation(CsvField.class);
            
            f.setAccessible(true);
            Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(f.getType());
            var csvFieldMeta = new CsvFieldMeta(f, key, index, type, csvField);
            
            if (byFieldName.putIfAbsent(fieldName, csvFieldMeta) != null) {
                log.error("[csv] duplicate field name: {} in class : {}", fieldName, className);
                throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
            }
            if (byKey.putIfAbsent(key, csvFieldMeta) != null) {
                log.error("[csv] duplicate header key: {} in class : {}", key, className);
                throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
            }
            
            fields.add(csvFieldMeta);
        
        }

        fields.sort(Comparator.comparingInt(CsvFieldMeta::index));
        
        var csvRequiredTogetherRules = new ArrayList<CsvRequiredTogetherRule>();
        var rtAnnotations = clazz.getAnnotationsByType(CsvRequiredTogether.class);
        
        for (var rt : rtAnnotations) {
            
            String[] names = rt.fields();
    
            var metas = new ArrayList<CsvFieldMeta>(names.length);
            var seen = new HashSet<String>();
    
            for (String name : names) {
                
                if (!seen.add(name)) {
                    log.error("[CsvRequiredTogetherRule] duplicate fieldName: {}, class : {}", name, className);
                    throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
                }
                
                var fieldName = byFieldName.get(name);
                
                if (fieldName == null) {
                    log.error("[CsvRequiredTogetherRule] unknown fieldName : {}, class : {}", name, className);
                    throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
                }
                
                metas.add(fieldName);
            }
    
            csvRequiredTogetherRules.add(new CsvRequiredTogetherRule(List.copyOf(metas)));
        }
        
        return new CsvClassMeta(clazz, List.copyOf(fields), byFieldName, byKey, csvRequiredTogetherRules);
    }
}
