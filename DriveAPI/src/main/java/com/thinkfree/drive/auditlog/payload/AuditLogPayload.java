package com.thinkfree.drive.auditlog.payload;

import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.auditlog.enums.EventName;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

// tb_audit_log 1 row에 대응하는 최종 전송 객체. AuditLogAspect가 공통 필드(Aspect가 직접 판정) +
// AuditLogContext(usecase/service가 ThreadLocal에 채운 값)를 합쳐서 만든다.
@Getter
@Builder
@ToString
public class AuditLogPayload {
    private Instant eventRegisterDate;

    private Integer tenantSeq;
    private String tenantName;

    private Integer userSeq;
    private String userId;
    private String userName;

    private EventCategory eventCategory;
    private EventName eventName;

    private Long objectSeq;
    private String objectUserId;
    private String objectName;
    private String resourcePath;

    private Integer targetTenantSeq;
    private String targetTenantName;
    private Integer targetUserSeq;
    private String targetUserId;
    private String targetUserName;

    private String beforeValue;
    private String afterValue;

    private AccessMethod accessMethod;
    private String ipAddress;

    // 처리 결과. Aspect가 정상 종료/예외 경로에서 SUCCESS/FAILURE로 채운다(EventIn outcome.status로 매핑).
    private String outcomeStatus;
}
