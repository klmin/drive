package com.thinkfree.drive.csv.validation.constraint;

import com.thinkfree.drive.common.validation.EmailValidator;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvConstraint;
import org.springframework.stereotype.Component;

@Component
public class EmailConstraint implements CsvConstraint {
    @Override
    public void validate(String value) throws CsvException {
        var result = EmailValidator.isValid(value);
        if(!result){
            throw CsvException.error(CsvErrorCode.INVALID_VALUE, value);
        }
    }
    
}
