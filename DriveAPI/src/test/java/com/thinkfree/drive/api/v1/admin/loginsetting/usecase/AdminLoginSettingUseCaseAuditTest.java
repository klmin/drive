package com.thinkfree.drive.api.v1.admin.loginsetting.usecase;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.api.v1.admin.loginsetting.converter.AdminLoginSettingConverter;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AdminLoginSettingUseCase가 <b>SettingAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * SETTING_UPDATE가 채우는 context 내용은 {@code DefaultSettingAuditServiceTest}에서 검증한다.
 */
@DisplayName("AdminLoginSettingUseCase 감사 로그 기록 흐름 테스트")
class AdminLoginSettingUseCaseAuditTest extends AuditMockTest {

    private static final int TENANT = 1;

    @Mock private AdminLoginSettingConverter adminLoginSettingConverter;
    @Mock private TenantFindService tenantFindService;
    @Mock private TenantService tenantService;
    @Mock private SsoProviderFindService ssoProviderFindService;
    @Mock private ClientDomainService clientDomainService;
    @Mock private SsoProviderService ssoProviderService;
    @Mock private SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    @Mock private SettingAuditLookupService settingAuditLookupService;
    @Mock private SettingAuditService settingAuditService;

    @InjectMocks
    private AdminLoginSettingUseCase adminLoginSettingUseCase;

    @Test
    @DisplayName("save - 로그인 방식이 OIDC→LOCAL로 바뀌면 변경 전/후 값으로 recordLoginMethodChangeIfNeeded를 호출한다")
    void save_loginTypeChanged_recordsSettingUpdate() {
        TenantContext.set(TENANT);
        given(superAdminLoginSettingFindService.find()).willReturn(null); // 슈퍼어드민 강제 아님
        given(settingAuditLookupService.findTenantLoginType(anyInt())).willReturn(LoginType.OIDC); // 변경 전

        // 요청: LOCAL로 변경 (LOCAL이면 SSO provider 동기화 분기를 타지 않음)
        var request = new LoginSettingSaveRequest(LoginType.LOCAL, null);

        adminLoginSettingUseCase.save(request);

        verify(settingAuditService).recordLoginMethodChangeIfNeeded(LoginType.OIDC, LoginType.LOCAL);
    }
}
