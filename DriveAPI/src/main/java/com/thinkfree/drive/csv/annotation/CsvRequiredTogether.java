package com.thinkfree.drive.csv.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(CsvRequiredTogethers.class)
public @interface CsvRequiredTogether {
    String[] fields();
}

