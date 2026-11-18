package com.thinkfree.drive.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 익명 유저(ROLE_ANONYMOUS)가 접근 가능한 API 메서드에 붙이는 마커 어노테이션.
 * 어노테이션의 존재 자체가 허용 의미를 가지므로 별도 속성 x
 * {@link com.thinkfree.drive.global.security.AnonymousPathRegistry}가 스캔하여 허용 경로로 등록한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AnonymousAccessible {
}
