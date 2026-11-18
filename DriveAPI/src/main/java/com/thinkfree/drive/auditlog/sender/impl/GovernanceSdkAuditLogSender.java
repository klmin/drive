package com.thinkfree.drive.auditlog.sender.impl;

import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.auditlog.sender.AuditLogSender;
import com.thinkfree.governance.sdk.client.GovernanceClient;
import com.thinkfree.governance.sdk.client.IngestResult;
import com.thinkfree.governance.sdk.json.jackson.JacksonJsonCodec;
import com.thinkfree.governance.sdk.model.EventIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 감사 로그를 Governance 서버로 전송하는 SDK 구현체. {@code governance.audit.enabled=true}일 때만 빈으로 등록된다
 * (그 외에는 {@link LoggingAuditLogSender}가 동작). 전송은 별도 executor에서 비동기로 수행하며,
 * 서버가 요청을 접수(202 accepted / 200 duplicate)하면 성공으로 본다 — STORED 저장 완료는 서버 책임이라 폴링하지 않는다.
 * 전송 실패는 원본 비즈니스 흐름에 영향을 주면 안 되므로 여기서 삼키고 로그만 남긴다.
 * 실제 전송 바디(actor 이메일·IP·country 등 개인정보 포함)는 INFO에는 남기지 않고, 필요할 때만
 * DEBUG로 켜서 확인한다.
 */
@Slf4j
@RequiredArgsConstructor
public class GovernanceSdkAuditLogSender implements AuditLogSender {

    private final GovernanceClient client;
    private final AuditLogPayloadToEventInMapper mapper;
    private final Executor executor;
    private final JacksonJsonCodec debugCodec = new JacksonJsonCodec();

    @Override
    public void send(List<AuditLogPayload> payloads) {
        for (AuditLogPayload payload : payloads) {
            executor.execute(() -> sendOne(payload));
        }
    }

    private void sendOne(AuditLogPayload payload) {
        try {
            EventIn event = mapper.toEventIn(payload);
            if (event == null) {
                log.debug("[AuditLog] skip - governance eventType not mapped yet: {}", payload.getEventName());
                return;
            }
            logEventBody(event);
            IngestResult result = client.ingestEvent(event);
            log.info("[AuditLog] sent {} status={} eventId={} receiptId={}",
                    payload.getEventName(), result.getStatus(), result.getEventId(), result.getReceiptId());
        } catch (Exception e) {
            log.error("[AuditLog] governance send failed - dropped: {}", payload.getEventName(), e);
        }
    }

    // 실제 전송 바디 확인용(DEBUG 전용). 직렬화 실패가 전송 자체를 막으면 안 되므로 별도로 삼킨다.
    private void logEventBody(EventIn event) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            log.debug("[AuditLog] event body: {}", new String(debugCodec.write(event), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[AuditLog] failed to serialize event body for debug log", e);
        }
    }
}
