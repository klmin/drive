package com.thinkfree.drive.auditlog.sender;

import com.thinkfree.drive.auditlog.payload.AuditLogPayload;

import java.util.List;

/**
 * 감사 로그를 로그 서버로 전송하는 경계. 응용개발팀 SDK/API 계약이 아직 미확정이라
 * 실제 전송 방식(REST 직접 호출 -> 추후 SDK 교체)을 이 인터페이스 뒤로 감춘다.
 * Aspect는 이 인터페이스에만 의존하므로, 계약이 확정되면 구현체만 교체하면 된다.
 */
public interface AuditLogSender {

    /**
     * @throws AuditLogSendException 전송 실패 시(네트워크 오류, 로그 서버 오류 응답 등)
     */
    void send(List<AuditLogPayload> payloads);
}
