package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 외부 경로({@code /api/public/v1}) 호출에 요구되는 API Key scope — 쓰기 endpoint 에 WRITE 를 붙인다.
 * <p>
 * 읽기는 선언하지 않는다. 외부 체인 게이트가 {@code api:*} 를 하나라도 요구하는데,
 * 발급 조합이 읽기 전용/읽기+쓰기 두 가지뿐이라(쓰기⊇읽기) 게이트 통과가 곧 읽기 권한 보유다.
 * {@code McpToolScope} 와 같은 계약이다.
 * <p>
 * 선언이 없으면 검사하지 않는다(default-open) — 개방 endpoint 대부분이 대상이 아니기 때문이다.
 * 툴 선언 누락을 거부로 처리하는 {@code McpToolRole}(default-deny)과 반대이며, 의도된 차이다.
 * <p>
 * {@code @ExternalApi} 가 함께 붙어야 의미가 있다 — 외부 경로에 등록되지 않으면 검사할 요청 자체가 없다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredApiScope {

    ApiKeyScope value();
}
