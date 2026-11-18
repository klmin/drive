package com.thinkfree.drive.auditlog.sender.impl;

import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.auditlog.sender.AuditLogSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// governance.audit.enabled=true면 GovernanceSdkAuditLogSender가 대신 뜨고, 그 외(기본)에는 로깅만 하는 이 구현이 뜬다.
@Slf4j
@Component
@ConditionalOnProperty(prefix = "governance.audit", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingAuditLogSender implements AuditLogSender {

    @Override
    public void send(List<AuditLogPayload> payloads) {
        payloads.forEach(payload -> log.info("[AuditLog] {} {}", payload.getEventName(), payload));
    }
}
