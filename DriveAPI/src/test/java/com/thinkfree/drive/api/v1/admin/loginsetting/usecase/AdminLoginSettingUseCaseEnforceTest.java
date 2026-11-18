package com.thinkfree.drive.api.v1.admin.loginsetting.usecase;

import com.thinkfree.drive.api.v1.admin.loginsetting.converter.AdminLoginSettingConverter;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingRequest;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** 관리자 로그인 설정 조회 — 슈퍼관리자 전체 적용 여부는 로그인 판정(LoginConfigService)과 같은 기준(SYSTEM_MANAGED 는 무시) */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLoginSettingUseCase.get — 슈퍼관리자 전체 적용 판정")
class AdminLoginSettingUseCaseEnforceTest {

    private static final int TENANT = 7;

    @Mock private AdminLoginSettingConverter adminLoginSettingConverter;
    @Mock private TenantFindService tenantFindService;
    @Mock private TenantService tenantService;
    @Mock private SsoProviderFindService ssoProviderFindService;
    @Mock private ClientDomainService clientDomainService;
    @Mock private SsoProviderService ssoProviderService;
    @Mock private SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    @Mock private SettingAuditLookupService settingAuditLookupService;
    @Mock private SettingAuditService settingAuditService;
    @Mock private TenantPolicy tenantPolicy;

    @InjectMocks
    private AdminLoginSettingUseCase adminLoginSettingUseCase;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static SuperAdminLoginSettingData enforced(LoginType loginType) {
        Integer seq = 1; // 박싱 없이 원시 boolean 정식 생성자를 고르게 한다(보조 생성자 Boolean 과 모호)
        return new SuperAdminLoginSettingData(seq, loginType, true, seq, null, null, null);
    }

    @Test
    @DisplayName("전체 적용을 허용하는 테넌트 — 슈퍼관리자 로그인 방식·superAdminEnforced=true")
    void enforcedTenant() {
        TenantContext.set(TENANT);
        given(superAdminLoginSettingFindService.find()).willReturn(enforced(LoginType.LOCAL));
        given(tenantPolicy.allows(TENANT, TenantFeature.LOGIN_SETTING)).willReturn(true);

        var response = adminLoginSettingUseCase.get(new LoginSettingRequest(null));

        assertThat(response.superAdminEnforced()).isTrue();
        assertThat(response.loginType()).isEqualTo(LoginType.LOCAL);
    }

    @Test
    @DisplayName("SYSTEM_MANAGED(전체 적용 무시) — 슈퍼관리자가 OIDC 전체 적용 중이어도 테넌트 설정·superAdminEnforced=false")
    void systemManagedIgnoresEnforce() {
        TenantContext.set(TENANT);
        given(superAdminLoginSettingFindService.find()).willReturn(enforced(LoginType.OIDC));
        given(tenantPolicy.allows(TENANT, TenantFeature.LOGIN_SETTING)).willReturn(false);
        given(tenantFindService.findByTenantSeq(TENANT)).willReturn(TenantData.builder().tenantSeq(TENANT).loginType(LoginType.LOCAL).build());

        var response = adminLoginSettingUseCase.get(new LoginSettingRequest(null));

        assertThat(response.superAdminEnforced()).isFalse();
        assertThat(response.loginType()).isEqualTo(LoginType.LOCAL);
        assertThat(response.configs()).isEmpty();
    }
}
