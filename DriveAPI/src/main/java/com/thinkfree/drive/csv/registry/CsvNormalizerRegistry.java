package com.thinkfree.drive.csv.registry;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvNormalizer;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CsvNormalizerRegistry {
    
    private final ConcurrentMap<Class<? extends CsvNormalizer>, CsvNormalizer> normalizerMap = new ConcurrentHashMap<>();
    
    public CsvNormalizerRegistry(List<CsvNormalizer> normalizers){
        for (var n : normalizers) {
            Class<? extends CsvNormalizer> impl = AopUtils.getTargetClass(n).asSubclass(CsvNormalizer.class);
            normalizerMap.put(impl, n);
        }
    }
    
    public CsvNormalizer get(Class<? extends CsvNormalizer> c) {
        var normalizer = normalizerMap.get(c);
        if(normalizer == null){
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }
        return normalizer;
    }
    
}
