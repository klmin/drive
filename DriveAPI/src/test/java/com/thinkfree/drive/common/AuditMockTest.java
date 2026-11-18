package com.thinkfree.drive.common;

import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.global.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 감사 로그 "기록 내용" 테스트(Mockito 방식)의 공통 base.
 * 각 테스트는 SUT별 @Mock/@InjectMocks만 자기 클래스에 두고, 이 base를 상속해
 * 공통 애노테이션과 ThreadLocal 정리를 물려받는다.
 * <p>
 * 특히 {@link #clearAuditContext()}를 base에서 강제하는 것이 핵심 — 각 테스트가 홀더 clear를
 * 빠뜨리면 테스트 간 {@link AuditLogContextHolder} 오염이 조용히 발생하므로 여기서 일괄 처리한다.
 * <p>
 * 실DB 통합 방식(예: AuthServiceTest는 {@code ServiceTest} 상속)이나 홀더를 쓰지 않는 테스트
 * (예: DefaultAuditLogPayloadValidatorTest)는 이 base 대상이 아니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AuditMockTest {

    @AfterEach
    void clearAuditContext() {
        AuditLogContextHolder.clear();
        TenantContext.clear();
    }
}
