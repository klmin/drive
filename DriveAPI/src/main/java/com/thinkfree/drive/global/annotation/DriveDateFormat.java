package com.thinkfree.drive.global.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.thinkfree.drive.constants.DriveConstants;

import java.lang.annotation.*;

@Documented
@JacksonAnnotationsInside
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@JsonFormat(pattern = DriveConstants.YYYY_MM_DD)
public @interface DriveDateFormat {
}
