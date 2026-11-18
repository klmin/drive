package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.result.LoginConfigResult;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.data.SsoLoginConfigData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginConfigService {

    private final TenantFindService tenantFindService;
    private final SsoProviderFindService ssoProviderFindService;
    private final SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    private final TenantPolicy tenantPolicy;

    public LoginConfigResult getLoginConfig(int tenantSeq) {

        SuperAdminLoginSettingData superSetting = superAdminLoginSettingFindService.find();
        boolean enforced = isEnforcedFor(superSetting, tenantSeq);
        LoginType loginType = enforced
                ? superSetting.loginType()
                : tenantFindService.findByTenantSeq(tenantSeq).getLoginType();

        if (loginType == LoginType.LOCAL) {
            SsoLoginConfigData ssoLoginConfigData = enforced ?
                    ssoProviderFindService.findLoginConfigByGlobalAndProtocolOrNull(SsoProviderProtocolType.JWT) :
                    ssoProviderFindService.findLoginConfigByProtocolOrNull(tenantSeq, SsoProviderProtocolType.JWT);
            
            if(ssoLoginConfigData == null){
                return new LoginConfigResult(LoginType.LOCAL, null, null);
            }
            return new LoginConfigResult(LoginType.LOCAL, null, ssoLoginConfigData.getErrorRedirectUrl());
        }

        SsoProviderProtocolType protocolType = toProtocolType(loginType);
        SsoLoginConfigData data = enforced
                ? ssoProviderFindService.findActiveByGlobalAndProtocolOrThrow(protocolType)
                : ssoProviderFindService.findActiveByTenantAndProtocolOrThrow(tenantSeq, protocolType);

        return new LoginConfigResult(loginType, data.getSsoProviderSeq(), data.getErrorRedirectUrl());
    }

    public void validateLoginType(int tenantSeq, LoginType required) {
        LoginType loginType = resolveEffectiveLoginType(tenantSeq);

        if (loginType != required) {
            throw ApiRuntimeException.type(AuthErrorCode.LOGIN_TYPE_MISMATCH);
        }
    }

    public LoginType resolveEffectiveLoginType(int tenantSeq) {
        SuperAdminLoginSettingData superSetting = superAdminLoginSettingFindService.find();
        if (isEnforcedFor(superSetting, tenantSeq)) {
            return superSetting.loginType();
        }
        TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
        return tenant.getLoginType();
    }

    /** 이 테넌트에 슈퍼관리자 로그인 설정 전체 적용(enforce)이 걸리는지 — SYSTEM_MANAGED 는 무시하고 테넌트 설정(로컬)을 쓴다 */
    public boolean isSuperAdminEnforced(int tenantSeq) {
        return isEnforcedFor(superAdminLoginSettingFindService.find(), tenantSeq);
    }

    private boolean isEnforcedFor(SuperAdminLoginSettingData superSetting, int tenantSeq) {
        return superSetting != null && superSetting.isEnforced() && tenantPolicy.allows(tenantSeq, TenantFeature.LOGIN_SETTING);
    }

    private SsoProviderProtocolType toProtocolType(LoginType loginType) {
        return switch (loginType) {
            case OIDC -> SsoProviderProtocolType.OIDC;
            case JWT  -> SsoProviderProtocolType.JWT;
            default   -> throw ApiRuntimeException.type(AuthErrorCode.LOGIN_TYPE_MISMATCH);
        };
    }
}
