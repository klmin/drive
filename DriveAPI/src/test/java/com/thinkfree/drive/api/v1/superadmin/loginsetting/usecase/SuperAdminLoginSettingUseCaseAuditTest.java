package com.thinkfree.drive.api.v1.superadmin.loginsetting.usecase;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.api.v1.superadmin.loginsetting.converter.SuperAdminLoginSettingRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingSaveRequest;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SuperAdminLoginSettingUseCase가 <b>SettingAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SETTING_UPDATE의 테넌트별 fan-out 내용은 {@code DefaultSettingAuditServiceTest}에서 검증한다.
 */
@DisplayName("SuperAdminLoginSettingUseCase 감사 로그 기록 흐름 테스트")
class SuperAdminLoginSettingUseCaseAuditTest extends AuditMockTest {

    @Mock private SuperAdminLoginSettingRequestConverter superAdminLoginSettingRequestConverter;
    @Mock private SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    @Mock private SuperAdminLoginSettingService superAdminLoginSettingService;
    @Mock private SsoProviderFindService ssoProviderFindService;
    @Mock private SsoProviderService ssoProviderService;
    @Mock private SettingAuditLookupService settingAuditLookupService;
    @Mock private SettingAuditService settingAuditService;

    @InjectMocks
    private SuperAdminLoginSettingUseCase superAdminLoginSettingUseCase;

    @Test
    @DisplayName("upsert - 전역 로그인 강제 GLOBAL→TENANT(off)로 바뀌면 변경 전/후 값으로 recordGlobalLoginPolicyChangeIfNeeded를 호출한다")
    void upsert_globalLoginPolicyOff_recordsSettingUpdate() {
        given(settingAuditLookupService.isGlobalLoginEnforced()).willReturn(true); // 변경 전: 전체 적용

        // ie=false → 전체 적용 해제(GLOBAL→TENANT)
        var request = new SuperAdminLoginSettingSaveRequest(null, false, null);

        superAdminLoginSettingUseCase.upsert(request);

        verify(settingAuditService).recordGlobalLoginPolicyChangeIfNeeded(true, false);
    }
}
