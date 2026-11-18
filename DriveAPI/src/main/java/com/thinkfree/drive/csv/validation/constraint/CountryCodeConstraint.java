package com.thinkfree.drive.csv.validation.constraint;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.validation.CsvConstraint;
import com.thinkfree.drive.user.enums.CountryCode;
import org.springframework.stereotype.Component;

@Component
public class CountryCodeConstraint implements CsvConstraint {
    
    @Override
    public void validate(String value) throws CsvException {
        var countryCode = CountryCode.fromCode(value);
        if(countryCode == null){
            throw CsvException.error(CsvErrorCode.INVALID_VALUE, value);
        }
    }
}
