package com.thinkfree.drive.global.time;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instant 응답 필드를 ISO 로컬 형식({@code yyyy-MM-ddTHH:mm:ss}, 설정 시간대 벽시계)으로 출력한다.
 * 형식 어노테이션 없이 LocalDateTime 으로 나가던 응답 필드를 Instant 로 바꿀 때 출력 문자열을 유지하는 용도.
 * 새 응답 필드는 {@link com.thinkfree.drive.global.annotation.DriveDateTimeFormat} 을 쓴다.
 */
@Documented
@JacksonAnnotationsInside
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@JsonSerialize(using = InstantAsLocalDateTimeSerializer.class)
public @interface DriveIsoLocalDateTimeFormat {
}
