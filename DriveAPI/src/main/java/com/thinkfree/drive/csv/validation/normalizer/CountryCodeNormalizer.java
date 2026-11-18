package com.thinkfree.drive.csv.validation.normalizer;

import com.thinkfree.drive.csv.validation.CsvNormalizer;
import org.springframework.stereotype.Component;

@Component
public class CountryCodeNormalizer implements CsvNormalizer {
    @Override
    public String normalize(String value) {
        return value.replaceAll("\\+", "");
    }
}
