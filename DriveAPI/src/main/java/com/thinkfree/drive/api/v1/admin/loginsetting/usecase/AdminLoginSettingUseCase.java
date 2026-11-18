package com.thinkfree.drive.api.v1.admin.loginsetting.usecase;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.api.v1.admin.loginsetting.converter.AdminLoginSettingConverter;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingRequest;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.admin.loginsetting.response.LoginSettingResponse;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.sso.constants.SsoConstants;
import com.thinkfree.drive.sso.protocol.exception.SsoProtocolErrorCode;
import com.thinkfree.drive.sso.provider.exception.SsoProviderErrorCode;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminLoginSettingUseCase {

    private final AdminLoginSettingConverter adminLoginSettingConverter;
    private final TenantFindService tenantFindService;
    private final TenantService tenantService;
    private final SsoProviderFindService ssoProviderFindService;
    private final ClientDomainService clientDomainService;
    private final SsoProviderService ssoProviderService;
    private final SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    private final SettingAuditLookupService settingAuditLookupService;
    private final SettingAuditService settingAuditService;
    private final TenantPolicy tenantPolicy;

    public LoginSettingResponse get(LoginSettingRequest request) {
        int tenantSeq = TenantContext.getOrThrow();
        var superSetting = superAdminLoginSettingFindService.find();
        // 로그인 판정(LoginConfigService)과 같은 기준 — SYSTEM_MANAGED 는 전체 적용을 무시하고 테넌트 설정을 보여 준다
        boolean enforced = superSetting != null && superSetting.isEnforced() && tenantPolicy.allows(tenantSeq, TenantFeature.LOGIN_SETTING);

        if (enforced) {
            
            LoginType loginType = superSetting.loginType();

            if (LoginType.LOCAL == loginType) {
                return new LoginSettingResponse(LoginType.LOCAL, null, true, List.of());
            }

            String redirectUri = LoginType.OIDC == loginType ? clientDomainService.getDomainBackEnd(tenantSeq) + SsoConstants.Oidc.OIDC_CALLBACK_PATH : null;
            var protocolType = adminLoginSettingConverter.toProtocolType(loginType);
            var configs = ssoProviderFindService.findAllGlobalDetailsByProtocol(protocolType)
                                                .stream()
                                                .map(adminLoginSettingConverter::toConfigResponse)
                                                .toList();

            return new LoginSettingResponse(loginType, redirectUri, true, configs);
        }
        
        var tenant = tenantFindService.findByTenantSeq(tenantSeq);
        var requestLoginType = request.loginType();
        LoginType loginType = requestLoginType == null ? tenant.getLoginType() : requestLoginType;

        if (LoginType.LOCAL == loginType) {
            return new LoginSettingResponse(LoginType.LOCAL, null, false, List.of());
        }

        String redirectUri = LoginType.OIDC == loginType ? clientDomainService.getDomainBackEnd(tenantSeq) + SsoConstants.Oidc.OIDC_CALLBACK_PATH : null;
        var protocolType = adminLoginSettingConverter.toProtocolType(loginType);
        var configs = ssoProviderFindService.findAllDetailsByTenantAndProtocol(tenant.getTenantSeq(), protocolType)
                                            .stream()
                                            .map(adminLoginSettingConverter::toConfigResponse)
                                            .toList();

        return new LoginSettingResponse(loginType, redirectUri, false, configs);
    }

    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.LOGIN_SETTING)
    public void save(LoginSettingSaveRequest request) {

        var superSetting = superAdminLoginSettingFindService.find();
        DriveAssert.throwIfTrue(superSetting != null && superSetting.isEnforced(), SsoProviderErrorCode.SUPER_ADMIN_LOGIN_ENFORCED);

        int tenantSeq = TenantContext.getOrThrow();
        var loginType = request.loginType();

        LoginType beforeLoginType = settingAuditLookupService.findTenantLoginType(tenantSeq);
        tenantService.updateLoginType(tenantSeq, loginType);
        settingAuditService.recordLoginMethodChangeIfNeeded(beforeLoginType, loginType);

        if (loginType == LoginType.LOCAL) {
            return;
        }

        var protocolType = adminLoginSettingConverter.toProtocolType(loginType);
        var upsertDtoList = adminLoginSettingConverter.toSsoProviderUpsertDtoList(request.providers(), protocolType, tenantSeq);

        switch (protocolType) {
            case JWT  -> ssoProviderService.syncJwt(upsertDtoList.get(0));
            case OIDC -> ssoProviderService.syncOidcList(tenantSeq, upsertDtoList);
            default   -> throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL);
        }
    }
}
