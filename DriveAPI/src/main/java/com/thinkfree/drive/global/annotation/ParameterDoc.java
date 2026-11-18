package com.thinkfree.drive.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터({@code @PathVariable}·{@code @RequestParam})의 OpenAPI 설명 — 표시 이름은 {@code parameters[].schema.title},
 * 설명은 {@code parameters[].description} 으로 나간다. 판정·바인딩에는 영향이 없다.
 * <p>
 * swagger {@code @Parameter} 를 쓰지 않는 이유: springdoc(2.8.17)은 {@code @Parameter} 가 붙으면 description 만 있어도
 * 추론 타입을 버리고 정수 파라미터를 {@code type: string, default: ""} 로 내보낸다. 이 어노테이션은
 * {@code DriveOperationExtensionCustomizer} 가 springdoc 이 만든 파라미터에 title·description 만 더하므로 타입이 그대로다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterDoc {

    /** 표시 이름(예: User ID) — 비우면 싣지 않는다 */
    String title() default "";

    /** 설명 — 비우면 싣지 않는다 */
    String description() default "";
}
