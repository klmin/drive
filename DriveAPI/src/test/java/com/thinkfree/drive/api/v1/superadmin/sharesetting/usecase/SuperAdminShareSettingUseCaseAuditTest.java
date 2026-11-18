package com.thinkfree.drive.api.v1.superadmin.sharesetting.usecase;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.api.v1.superadmin.sharesetting.converter.SuperAdminShareSettingRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.request.SuperAdminShareSettingSaveRequest;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.cleanup.share.global.service.GlobalShareReconcileService;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.share.setting.service.ShareSettingFindService;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SuperAdminShareSettingUseCase가 <b>SettingAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SETTING_UPDATE의 테넌트별 fan-out 내용은 {@code DefaultSettingAuditServiceTest}에서 검증한다.
 */
@DisplayName("SuperAdminShareSettingUseCase 감사 로그 기록 흐름 테스트")
class SuperAdminShareSettingUseCaseAuditTest extends AuditMockTest {

    @Mock private ShareSettingFindService shareSettingFindService;
    @Mock private ShareSettingService shareSettingService;
    @Mock private ShareSettingFacade shareSettingFacade;
    @Mock private SystemConfigFindService systemConfigFindService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private TenantFindService tenantFindService;
    @Mock private GlobalShareReconcileService globalShareReconcileService;
    @Mock private SuperAdminShareSettingRequestConverter requestConverter;
    @Mock private SettingAuditLookupService settingAuditLookupService;
    @Mock private SettingAuditService settingAuditService;

    @InjectMocks
    private SuperAdminShareSettingUseCase superAdminShareSettingUseCase;

    @Test
    @DisplayName("upsert - 전역 공유정책 강제 GLOBAL→TENANT(off)로 바뀌면 변경 전/후 값으로 recordGlobalSharePolicyChangeIfNeeded를 호출한다")
    void upsert_globalSharePolicyOff_recordsSettingUpdate() {
        given(settingAuditLookupService.isGlobalShareEnforced()).willReturn(true); // 변경 전: 전체 적용

        // e=false → 전체 적용 해제(GLOBAL→TENANT)
        var request = new SuperAdminShareSettingSaveRequest(false, null, null, null, null, null);

        superAdminShareSettingUseCase.upsert(request);

        verify(settingAuditService).recordGlobalSharePolicyChangeIfNeeded(true, false);
    }
}
