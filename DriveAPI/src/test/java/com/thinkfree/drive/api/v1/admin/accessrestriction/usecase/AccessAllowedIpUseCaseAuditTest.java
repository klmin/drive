package com.thinkfree.drive.api.v1.admin.accessrestriction.usecase;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedIpsUpdateRequest;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.global.security.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AccessAllowedIpUseCase가 <b>SettingAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SETTING_ALLOWED_IP_CHANGE가 채우는 context 내용(델타 계산 등)은 {@code DefaultSettingAuditServiceTest}에서 검증한다.
 */
@DisplayName("AccessAllowedIpUseCase(허용 IP 변경) 감사 로그 기록 흐름 테스트")
class AccessAllowedIpUseCaseAuditTest extends AuditMockTest {

    private static final int TENANT = 1;
    private static final int LOGIN_USER = 9;

    @Mock private AccessAllowedIpService accessAllowedIpService;
    @Mock private SettingAuditService settingAuditService;

    @InjectMocks
    private AccessAllowedIpUseCase accessAllowedIpUseCase;

    @Test
    @DisplayName("putAllowedIps - 변경 전/후 IP 목록으로 SettingAuditService.recordAllowedIpChangeIfNeeded를 호출한다")
    void putAllowedIps_recordsAllowedIpChange() {
        TenantContext.set(TENANT);
        given(accessAllowedIpService.findAllIpCidrs(anyInt(), any())).willReturn(List.of("1.1.1.1/32"));

        var request = new AllowedIpsUpdateRequest(List.of("2.2.2.2/32"));
        accessAllowedIpUseCase.putAllowedIps(request, LOGIN_USER);

        verify(settingAuditService).recordAllowedIpChangeIfNeeded(List.of("1.1.1.1/32"), List.of("2.2.2.2/32"));
    }
}
