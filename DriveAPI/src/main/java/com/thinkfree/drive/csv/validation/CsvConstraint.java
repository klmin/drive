package com.thinkfree.drive.csv.validation;

import com.thinkfree.drive.csv.exception.CsvException;

public interface CsvConstraint {
     void validate(String value) throws CsvException;
}
