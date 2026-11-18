package com.thinkfree.drive.auditlog.validator;

import com.thinkfree.drive.auditlog.payload.AuditLogPayload;

/**
 * 전송 전 {@link AuditLogPayload}가 자신의 eventName에 요구되는 필드(required key)와
 * before/after의 required json key를 다 채웠는지 검사한다. 상세 규칙은 5번 단계에서 구현.
 */
public interface AuditLogPayloadValidator {

    void validate(AuditLogPayload payload);
}
