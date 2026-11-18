package com.thinkfree.drive.api.v1.admin.sharesetting.usecase;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.api.v1.admin.sharesetting.converter.AdminShareSettingRequestConverter;
import com.thinkfree.drive.api.v1.admin.sharesetting.request.AdminShareSettingSaveRequest;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.cleanup.share.global.service.GlobalShareReconcileService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import com.thinkfree.drive.share.setting.result.ResolvedShareSetting;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AdminShareSettingUseCase가 <b>SettingAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SETTING_UPDATE가 채우는 context 내용은 {@code DefaultSettingAuditServiceTest}에서 검증한다.
 */
@DisplayName("AdminShareSettingUseCase 감사 로그 기록 흐름 테스트")
class AdminShareSettingUseCaseAuditTest extends AuditMockTest {

    private static final int TENANT = 1;

    @Mock private ShareSettingFacade shareSettingFacade;
    @Mock private ShareSettingService shareSettingService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private GlobalShareReconcileService globalShareReconcileService;
    @Mock private AdminShareSettingRequestConverter requestConverter;
    @Mock private SettingAuditService settingAuditService;

    @InjectMocks
    private AdminShareSettingUseCase adminShareSettingUseCase;

    @Test
    @DisplayName("save - 외부 공유 허용이 OFF→ON으로 바뀌면 변경 전/후 값으로 recordExternalShareChangeIfNeeded를 호출한다")
    void save_externalShareToggled_recordsSettingUpdate() {
        TenantContext.set(TENANT);

        // 변경 전: 외부 공유 미허용(false), 슈퍼어드민 강제 아님
        var old = new EffectiveShareSetting(true, false, true, true, false);
        given(shareSettingFacade.resolveWithEnforced(anyInt()))
                .willReturn(new ResolvedShareSetting(false, old));

        // 요청: 공유 on + 외부 공유 허용(esa=true) → 외부 공유 ON
        var request = new AdminShareSettingSaveRequest(true, true, true, true, false);

        adminShareSettingUseCase.save(request);

        verify(settingAuditService).recordExternalShareChangeIfNeeded(false, true);
    }
}
