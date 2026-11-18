package com.thinkfree.drive.mcp.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 툴 파라미터(Integer apiKeySeq) 앞에 붙이면, {@link McpToolTenantAspect}가 해당 apiKeySeq가
 * 현재 요청의 테넌트(TenantContext)와 동일한 테넌트에 속하는지 검증한다.
 *
 * 웹 컨트롤러의 {@code @TenantApiKey}와 동일한 의미·검증(타테넌트·미존재 = API_KEY_NOT_FOUND로 은닉)이지만,
 * MCP 전용으로 분리한 이유는 {@link McpToolTenantUser}와 같다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolTenantApiKey {
}
