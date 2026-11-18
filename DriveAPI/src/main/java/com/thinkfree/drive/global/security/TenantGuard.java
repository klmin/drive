package com.thinkfree.drive.global.security;

import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantGuard {

    public static boolean isOwnedByCurrentTenant(int tenantSeq) {
        return TenantContext.getOrThrow() == tenantSeq;
    }

    public static void verifyOwnership(int tenantSeq, ErrorCode errorCode) {
        DriveAssert.throwIfTrue(!isOwnedByCurrentTenant(tenantSeq), errorCode);
    }
    
    public static void verifyOwnership(int tenantSeq, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!isOwnedByCurrentTenant(tenantSeq)) {
            throw exceptionSupplier.get();
        }
    }
}
