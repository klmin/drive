package com.thinkfree.drive.csv.annotation;

import com.thinkfree.drive.csv.validation.CsvConstraint;
import com.thinkfree.drive.csv.validation.CsvNormalizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface CsvField {
    int min() default 1;
    int max() default Integer.MAX_VALUE;
    boolean unique() default false;
    boolean required() default true;
    Class<? extends CsvNormalizer>[] normalizers() default {};
    Class<? extends CsvConstraint>[] constraints() default {};
}
