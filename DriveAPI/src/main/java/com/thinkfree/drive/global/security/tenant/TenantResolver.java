package com.thinkfree.drive.global.security.tenant;

import com.thinkfree.drive.global.exception.ErrorCode;

import java.util.function.Function;

/**
 * 자원 한 종류의 tenant 검증 방법을 캡슐화한다.
 *
 * @param idType          식별자 타입 (Integer.class / Long.class 등)
 * @param lookup          식별자 → 자원의 tenantSeq 조회 함수 (자원 없으면 자체 throw)
 * @param errorOnMismatch 현재 테넌트와 다를 때 throw 할 에러 코드 (404 권장, 자원 존재 노출 방지)
 * @param <T>             식별자 타입 (Number 하위)
 */
public record TenantResolver<T extends Number>(
        Class<T> idType,
        Function<T, Integer> lookup,
        ErrorCode errorOnMismatch) {

    /**
     * 파라미터 값(Object)을 idType으로 안전하게 캐스팅해 tenantSeq를 조회한다.
     * idType.cast 와 lookup.apply 가 동일한 캡처 타입 T를 공유하므로 unchecked 경고 없이 타입 안전하다.
     */
    public Integer resolveTenantSeq(Object value) {
        if (!idType.isInstance(value)) {
            throw new IllegalStateException(
                    "Tenant boundary parameter must be a " + idType.getSimpleName() + ". Found: " + value.getClass());
        }
        return lookup.apply(idType.cast(value));
    }
}
