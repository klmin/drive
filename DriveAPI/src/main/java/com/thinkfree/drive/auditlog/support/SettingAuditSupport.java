package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SettingAuditSupport {

    // SETTING_CHANGE shape(objectName/before/after) - 현재 테넌트 컨텍스트 기준 설정 변경
    public static void recordUpdate(String objectName, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SETTING_UPDATE)
                .objectName(objectName)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // SETTING_CHANGE shape + tenantSeq override - 슈퍼어드민 전역 정책 변경의 테넌트별 fan-out
    public static void recordUpdate(Integer tenantSeq, String objectName, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SETTING_UPDATE)
                .tenantSeq(tenantSeq)
                .objectName(objectName)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // VALUE_CHANGE shape(afterValue만) - 허용 IP 목록 델타(added/removed)
    public static void recordAllowedIpChange(String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SETTING_ALLOWED_IP_CHANGE)
                .afterValue(afterValue)
                .build());
    }
}
