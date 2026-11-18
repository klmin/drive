package com.thinkfree.drive.mcp.security;

import com.thinkfree.drive.oauth2.enums.OAuth2Scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 툴 요구 scope 선언 — {@link McpToolScopeAspect}가 읽어 강제한다.
 *
 * 쓰기 툴에 {@code @McpToolScope(OAuth2Scope.MCP_WRITE)}를 붙인다. 선언이 없는 툴은
 * 체인 게이트(McpSecurityConfig: mcp:* 하나라도) 수준으로 충분한 읽기 툴로 본다 —
 * 쓰기⊇읽기 계약이라 읽기 요구는 게이트 통과와 동치이므로 별도 선언이 불필요하다.
 * 메서드 선언이 클래스 선언보다 우선한다({@link McpToolRole}과 동일 규칙).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolScope {

    /** 이 툴 실행에 필요한 scope */
    OAuth2Scope value();
}
