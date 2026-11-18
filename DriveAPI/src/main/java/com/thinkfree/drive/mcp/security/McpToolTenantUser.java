package com.thinkfree.drive.mcp.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 툴 파라미터(Integer userSeq) 앞에 붙이면, {@link McpToolTenantAspect}가 해당 userSeq가
 * 현재 요청의 테넌트(TenantContext)와 동일한 테넌트에 속하는지 검증한다.
 *
 * 웹 컨트롤러의 {@code @TenantUser}와 동일한 의미·검증(타테넌트·미존재 = USER_NOT_FOUND로 은닉)이지만,
 * 웹 어노테이션을 재사용하지 않고 MCP 전용으로 분리한 이유: MCP 보안 어노테이션은 자체 체계
 * ({@link McpToolRole}·{@link McpToolScope})를 쓰는 것이 관례이고, 웹 쪽 어노테이션·포인트컷 변경이
 * MCP 경로에 조용히 전파되는 결합을 끊기 위함이다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolTenantUser {
}
