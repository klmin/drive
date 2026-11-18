package com.thinkfree.drive.global.security.tenant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터(Integer apiKeySeq) 앞에 붙이면, TenantBoundaryAspect가 해당 apiKey가
 * 현재 요청의 테넌트(TenantContext)와 동일한 테넌트에 속하는지 검증한다.
 *
 * <p>검증 실패 시 {@link com.thinkfree.drive.apikey.exception.ApiKeyErrorCode#API_KEY_NOT_FOUND}를 발생시켜
 * 다른 테넌트의 apiKey 존재 여부가 노출되지 않도록 한다 (404 통일).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantApiKey {
}
