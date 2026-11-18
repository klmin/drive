package com.thinkfree.drive.auditlog.aspect;

import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.auditlog.sender.AuditLogSender;
import com.thinkfree.drive.auditlog.support.AccessMethodResolver;
import com.thinkfree.drive.auditlog.validator.AuditLogPayloadValidator;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.util.IpUtil;
import com.thinkfree.drive.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @AuditLog}가 붙은 메서드가 끝나면 공통 필드를 채우고
 * {@link AuditLogContextHolder}에 쌓인 이벤트(+ 어노테이션이 지정한 bare 이벤트)를 합쳐 전송한다.
 * 감사 로그 처리 실패가 원본 비즈니스 로직에 영향을 주면 안 되므로, 이 Aspect는 예외를 삼킨다
 * ({@code proceed()} 자체에서 던진 예외는 그대로 다시 던진다 - 그건 비즈니스 예외이므로).
 * <p>
 * 전송 시점:
 * <ul>
 *   <li>정상 종료 → 진행 중인 트랜잭션이 있으면 <b>커밋된 뒤</b>에만 전송한다(롤백되면 전송 안 함).
 *       성공 로그가 롤백된 작업에 대해 남는 것을 막기 위함. 트랜잭션이 없으면 즉시 전송.
 *       payload는 요청 컨텍스트가 살아있는 지금 미리 만들어두고, 커밋 콜백은 만들어둔 payload만 전송한다
 *       (afterCommit 시점엔 ThreadLocal/요청 컨텍스트가 이미 정리됐을 수 있으므로).</li>
 *   <li>예외 종료 → 실패 자체가 감사 대상이므로 롤백 여부와 무관하게 즉시 전송.</li>
 * </ul>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogPayloadValidator auditLogPayloadValidator;
    private final AuditLogSender auditLogSender;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        try {
            Object result = pjp.proceed();
            recordAndSend(auditLog.eventName(), true);
            return result;
        } catch (Throwable t) {
            recordAndSend(auditLog.exceptionEventName(), false);
            throw t;
        } finally {
            AuditLogContextHolder.clear();
        }
    }

    // success=true(정상 종료) → outcome=SUCCESS이고 트랜잭션이 있으면 커밋 후 전송(없으면 즉시).
    // success=false(예외) → outcome=FAILURE이고 롤백 여부와 무관하게 즉시 전송.
    private void recordAndSend(EventName[] bareEventNames, boolean success) {
        boolean deferUntilCommit = success;
        try {
            List<AuditLogContext> contexts = AuditLogContextHolder.getAll();
            if (contexts.isEmpty() && bareEventNames.length == 0) {
                return;
            }

            List<AuditLogPayload> payloads = new ArrayList<>(contexts.size() + bareEventNames.length);
            for (AuditLogContext context : contexts) {
                payloads.add(toPayload(context.getEventName(), context, success));
            }
            for (EventName eventName : bareEventNames) {
                payloads.add(toPayload(eventName, null, success));
            }

            payloads.forEach(auditLogPayloadValidator::validate);

            if (deferUntilCommit && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send(payloads);
                    }
                });
            } else {
                send(payloads);
            }
        } catch (Exception e) {
            // 감사 로그 처리(빌드/검증/등록) 실패는 원본 비즈니스 흐름에 영향을 주지 않도록 여기서 삼킨다.
            log.error("[AuditLog] failed to record audit log - swallowed", e);
        }
    }

    private void send(List<AuditLogPayload> payloads) {
        try {
            auditLogSender.send(payloads);
            log.debug("[AuditLog] sent {} event(s)", payloads.size());
        } catch (Exception e) {
            log.error("[AuditLog] failed to send audit log - swallowed", e);
        }
    }

    private AuditLogPayload toPayload(EventName eventName, AuditLogContext context, boolean success) {
        Integer tenantSeq = (context != null && context.getTenantSeq() != null) ? context.getTenantSeq() : TenantContext.get();
        Actor actor = resolveActor(context);
        AccessMethod accessMethod = AccessMethodResolver.resolve(context != null ? context.getAccessMethod() : null);

        return AuditLogPayload.builder()
                .eventRegisterDate(Instant.now())
                .tenantSeq(tenantSeq)
                .userSeq(actor.userSeq())
                .userId(actor.userId())
                .userName(actor.userName())
                .eventCategory(eventName.getEventCategory())
                .eventName(eventName)
                .objectSeq(context != null ? context.getObjectSeq() : null)
                .objectUserId(context != null ? context.getObjectUserId() : null)
                .objectName(context != null ? context.getObjectName() : null)
                .resourcePath(stripTrailingSlash(context != null ? context.getResourcePath() : null))
                .targetTenantSeq(context != null ? context.getTargetTenantSeq() : null)
                .targetTenantName(context != null ? context.getTargetTenantName() : null)
                .targetUserSeq(context != null ? context.getTargetUserSeq() : null)
                .targetUserId(context != null ? context.getTargetUserId() : null)
                .targetUserName(context != null ? context.getTargetUserName() : null)
                .beforeValue(context != null ? context.getBeforeValue() : null)
                .afterValue(context != null ? context.getAfterValue() : null)
                .accessMethod(accessMethod)
                .ipAddress(resolveIpAddress())
                // context에 outcome override가 있으면 그대로 사용(상태 변화 이벤트 등), 없으면 메서드 종료 결과로 판정.
                .outcomeStatus(context != null && context.getOutcomeStatus() != null
                        ? context.getOutcomeStatus()
                        : (success ? "SUCCESS" : "FAILURE"))
                .build();
    }

    // resourcePath 표기용 trailing slash 제거. null/빈값·슬래시 없는 값은 그대로 둔다.
    private static String stripTrailingSlash(String path) {
        if (path == null || !path.endsWith("/")) {
            return path;
        }
        return path.substring(0, path.length() - 1);
    }

    private Actor resolveActor(AuditLogContext context) {
        if (context != null && context.getActorUserSeq() != null) {
            return new Actor(context.getActorUserSeq(), context.getActorUserId(), context.getActorUserName());
        }

        CustomUserDetails userDetails = SecurityUtil.getUserDetails();
        if (userDetails == null) {
            return new Actor(null, null, null);
        }
        return new Actor(userDetails.getUserSeq(), userDetails.getUserId(), userDetails.getUsername());
    }

    private String resolveIpAddress() {
        HttpServletRequest request = currentRequestOrNull();
        return request != null ? IpUtil.extractClientIp(request) : null;
    }

    private static HttpServletRequest currentRequestOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }

    private record Actor(Integer userSeq, String userId, String userName) {
    }
}
