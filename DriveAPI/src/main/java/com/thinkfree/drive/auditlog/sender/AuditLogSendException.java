package com.thinkfree.drive.auditlog.sender;

// AuditLogSender 구현체가 전송에 실패했을 때 던지는 예외. AuditLogAspect가 항상 잡아서 로깅만 하고
// 삼킨다 - 감사 로그 전송 실패가 원본 비즈니스 로직 흐름에 영향을 주면 안 되기 때문.
public class AuditLogSendException extends RuntimeException {

    public AuditLogSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
