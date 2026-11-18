package com.thinkfree.drive.global.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.thinkfree.drive.constants.DriveConstants;

import java.lang.annotation.*;

/**
 * 응답 시각 형식. Instant 는 {@code spring.jackson.time-zone}(= {@code drive.time-zone}) 벽시계로 출력한다 — 여기 timezone 을 박지 않는다.
 */
@Documented
@JacksonAnnotationsInside
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@JsonFormat(pattern = DriveConstants.YYYY_MM_DD_HH_MM_SS_SSS)
public @interface DriveDateTimeFormat {
}
