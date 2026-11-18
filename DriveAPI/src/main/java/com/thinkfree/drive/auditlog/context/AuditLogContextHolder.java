package com.thinkfree.drive.auditlog.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditLogContextHolder {

    private static final ThreadLocal<List<AuditLogContext>> CONTEXT = ThreadLocal.withInitial(ArrayList::new);

    // 대상자/파일 여러 건에 대해 row를 fan-out해야 하는 이벤트(SHARE_CREATE 등)를 위해 리스트로 누적한다.
    public static void add(AuditLogContext context) {
        CONTEXT.get().add(context);
    }

    public static List<AuditLogContext> getAll() {
        return Collections.unmodifiableList(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
