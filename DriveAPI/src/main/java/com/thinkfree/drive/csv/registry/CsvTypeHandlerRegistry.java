package com.thinkfree.drive.csv.registry;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CsvTypeHandlerRegistry {
    
    private final Map<Class<?>, CsvTypeHandler<?>> typeHandlerMap = new ConcurrentHashMap<>();
    
    public CsvTypeHandlerRegistry(List<CsvTypeHandler<?>> handlers){
        for (var h : handlers) {
            Class<?> implClass = AopUtils.getTargetClass(h);
            Class<?> generic = resolveGenericType(implClass);
            register(generic, h);
        }
    }
    
    private Class<?> resolveGenericType(Class<?> implClass) {
        ResolvableType rt = ResolvableType.forClass(implClass).as(CsvTypeHandler.class);
        return rt.getGeneric(0).resolve();
    }
    
    private void register(Class<?> type, CsvTypeHandler<?> h) {
        var old = typeHandlerMap.put(type, h);
        if (old != null && old != h) {
            log.error("CsvTypeHandler conflict on type {} : {} -> {}", type.getName(), old, h);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> CsvTypeHandler<T> get(Class<T> type) {
        var handler = typeHandlerMap.get(type);
        
        if (handler == null){
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }
        return (CsvTypeHandler<T>) handler;
    }
}