package com.thinkfree.drive.global.security;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantContext {

    private static final ThreadLocal<Integer> TENANT_SEQ = new ThreadLocal<>();

    public static void set(Integer tenantSeq) {
        TENANT_SEQ.set(tenantSeq);
    }

    public static Integer get() {
        return TENANT_SEQ.get();
    }

    public static Integer getOrThrow() {
        Integer tenantSeq = TENANT_SEQ.get();
        if (tenantSeq == null) {
            throw new IllegalStateException("TenantContext is not set. TenantResolverFilter should have populated it.");
        }
        return tenantSeq;
    }

    public static void clear() {
        TENANT_SEQ.remove();
    }
}
