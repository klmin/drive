package com.thinkfree.drive.csv.validation;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvClassMeta;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.model.CsvRequiredTogetherRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class CsvRowRequiredTogetherRowConstraint {
    
    public void validate(Object row, CsvClassMeta meta) {

        var rules = meta.requiredTogetherRules();
        
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (CsvRequiredTogetherRule rule : rules) {
            
            int present = 0;

            for (CsvFieldMeta fMeta : rule.fields()) {
                
                Object value = getValue(row, fMeta);
                
                if (isPresent(value)) {
                    present++;
                }
            }

            boolean ok = (present == 0) || (present == rule.fields().size());
            
            if (!ok) {
               throw CsvException.of(CsvErrorCode.MISSING_REQUIRED_FIELD);
            }
        }
    }

    private boolean isPresent(Object v) {
        return (v instanceof String s) ? StringUtils.hasText(s) : v != null;
    }

    private Object getValue(Object row, CsvFieldMeta fMeta) {
        try {
            return fMeta.field().get(row);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            log.error("[RequiredTogetherRowConstraint reflection failed] clazz : {}, fieldName : {}", row.getClass(), fMeta.field().getName());
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }
    }
    
}
