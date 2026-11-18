package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.user.enums.RoleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 툴 사용 가능 계정 role 선언 — {@link McpToolRoleAspect}가 읽어 강제한다.
 *
 * 클래스에 붙이면 그 클래스의 모든 @Tool에 적용되고, 메서드 선언이 있으면 메서드가
 * 우선한다(클래스 기본값 + 툴별 예외 구조). 선언이 어디에도 없는 @Tool은
 * 어스펙트가 실행을 거부한다(default-deny) — 새 툴 추가 시 선언 누락이
 * 조용한 권한 부여로 이어지지 않게 하는 장치.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolRole {

    /** 이 툴을 사용할 수 있는 계정 role 목록 (하나라도 일치하면 허용) */
    RoleType[] value();
}
