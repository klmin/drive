package com.thinkfree.drive.csv.validation.constraint;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvConstraint;
import com.thinkfree.drive.user.entity.Password;
import org.springframework.stereotype.Component;

@Component
public class PasswordConstraint implements CsvConstraint {
    @Override
    public void validate(String value) throws CsvException{
        var result = Password.isNotValidPattern(value);
        if(result){
            throw CsvException.error(CsvErrorCode.INVALID_VALUE, value);
        }
    }
}
