package com.thinkfree.drive.csv.registry;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvConstraint;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CsvConstraintRegistry {
    
    private final ConcurrentMap<Class<? extends CsvConstraint>, CsvConstraint> constraintMap = new ConcurrentHashMap<>();
    
    public CsvConstraintRegistry(List<CsvConstraint> constraints){
        for (var c : constraints) {
            Class<? extends CsvConstraint> impl = AopUtils.getTargetClass(c).asSubclass(CsvConstraint.class);
            constraintMap.put(impl, c);
        }
    }
    
    public CsvConstraint get(Class<? extends CsvConstraint> c) {
        var constraint = constraintMap.get(c);
        if(constraint == null){
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }
        return constraint;
    }
    
    
}
