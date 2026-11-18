package com.thinkfree.drive.auditlog.aspect;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventCategory;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.auditlog.sender.AuditLogSender;
import com.thinkfree.drive.auditlog.validator.AuditLogPayloadValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * AuditLogAspect 단위 테스트. 트랜잭션 컨텍스트가 없으므로 정상 종료 시 즉시 전송된다.
 * 특히 LOGOUT은 본문 record 호출 없이 선언형(@AuditLog(eventName=LOGOUT))으로만 생성되므로,
 * "선언형 bare 이벤트가 올바른 payload로 방출되는지"를 여기서 검증한다(홀더 방식으로는 불가).
 */
@DisplayName("AuditLogAspect 단위 테스트(선언형 이벤트 방출 포함)")
class AuditLogAspectTest extends AuditMockTest {

    @Mock private AuditLogPayloadValidator validator;
    @Mock private AuditLogSender sender;

    @Captor private ArgumentCaptor<List<AuditLogPayload>> payloadsCaptor;

    /** @AuditLog가 붙은 메서드를 프록시로 감싸 Aspect가 실제로 적용되도록 한다. */
    private Sample proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleImpl());
        factory.addAspect(new AuditLogAspect(validator, sender));
        return factory.getProxy();
    }

    // EventName: LOGOUT (선언형)
    @Test
    @DisplayName("정상 종료 시 @AuditLog(eventName=LOGOUT)는 LOGOUT payload를 방출한다")
    void declarativeLogout_emitsLogoutPayload() {
        proxy().logout();

        verify(sender).send(payloadsCaptor.capture());
        List<AuditLogPayload> sent = payloadsCaptor.getValue();
        assertThat(sent).hasSize(1);
        AuditLogPayload p = sent.get(0);
        assertThat(p.getEventName()).isEqualTo(EventName.LOGOUT);
        assertThat(p.getEventCategory()).isEqualTo(EventCategory.LOGIN);
        assertThat(p.getOutcomeStatus()).isEqualTo("SUCCESS");
        // 전송 전 검증기가 호출되고, 처리 후 홀더는 비워진다
        verify(validator, atLeastOnce()).validate(p);
        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    @Test
    @DisplayName("정상 종료 시 홀더에 쌓인 이벤트를 전송하고 홀더를 비운다")
    void normalCompletion_sendsHolderContextsAndClears() {
        proxy().withContext();

        verify(sender).send(payloadsCaptor.capture());
        List<AuditLogPayload> sent = payloadsCaptor.getValue();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getEventName()).isEqualTo(EventName.FILE_DELETE);
        assertThat(sent.get(0).getOutcomeStatus()).isEqualTo("SUCCESS");
        // resourcePath는 payload 단계에서 trailing slash가 제거된다 (영업팀/2026/ → 영업팀/2026)
        assertThat(sent.get(0).getResourcePath()).isEqualTo("영업팀/2026");
        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    @Test
    @DisplayName("예외 발생 시 exceptionEventName을 FAILURE로 방출하고 원본 예외를 다시 던진다")
    void exceptionPath_emitsExceptionEventAndRethrows() {
        assertThatThrownBy(() -> proxy().boom())
                .isInstanceOf(IllegalStateException.class);

        verify(sender).send(payloadsCaptor.capture());
        List<AuditLogPayload> sent = payloadsCaptor.getValue();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getEventName()).isEqualTo(EventName.LOGIN_FAILED);
        assertThat(sent.get(0).getOutcomeStatus()).isEqualTo("FAILURE");
        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // --- 프록시 대상(테스트 전용) ---
    interface Sample {
        void logout();
        void withContext();
        void boom();
    }

    static class SampleImpl implements Sample {
        @AuditLog(eventName = EventName.LOGOUT)
        @Override
        public void logout() {
            // 본문 없음 - LOGOUT은 선언형으로만 생성된다
        }

        @AuditLog
        @Override
        public void withContext() {
            AuditLogContextHolder.add(AuditLogContext.builder()
                    .eventName(EventName.FILE_DELETE)
                    .objectSeq(1L)
                    .objectName("파일.txt")
                    .resourcePath("영업팀/2026/")   // payload 단계에서 trailing slash 제거되는지 검증용
                    .build());
        }

        @AuditLog(exceptionEventName = EventName.LOGIN_FAILED)
        @Override
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
