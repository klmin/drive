package com.thinkfree.drive.api.v1.superadmin.loginsetting.usecase;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.converter.SuperAdminLoginSettingRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingRequest;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.response.SuperAdminLoginSettingResponse;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.sso.protocol.exception.SsoProtocolErrorCode;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuperAdminLoginSettingUseCase {

    private final SuperAdminLoginSettingRequestConverter superAdminLoginSettingRequestConverter;
    private final SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    private final SuperAdminLoginSettingService superAdminLoginSettingService;
    private final SsoProviderFindService ssoProviderFindService;
    private final SsoProviderService ssoProviderService;
    private final SettingAuditLookupService settingAuditLookupService;
    private final SettingAuditService settingAuditService;

    public SuperAdminLoginSettingResponse get(SuperAdminLoginSettingRequest request) {
        
        SuperAdminLoginSettingData setting = superAdminLoginSettingFindService.find();
        
        if (setting == null) {
            return new SuperAdminLoginSettingResponse(null, null, null, List.of());
        }
        
        Boolean isEnforced = setting.isEnforced();
        
        SsoProviderScope scope;
        
        if (request.s() != null) {
            scope = request.s();
        } else if (request.l() != null) {
            scope = SsoProviderScope.GLOBAL;
        } else {
            scope = isEnforced ? SsoProviderScope.GLOBAL : SsoProviderScope.TENANT;
        }
        LoginType loginType = request.l() == null ? setting.loginType() : request.l();
        Integer superAdminLoginSettingSeq = setting.superAdminLoginSettingSeq();
        
        if (scope == SsoProviderScope.TENANT) {
            return new SuperAdminLoginSettingResponse(superAdminLoginSettingSeq, null, false, List.of());
        }
        
        if (loginType == null || loginType == LoginType.LOCAL) {
            return new SuperAdminLoginSettingResponse(superAdminLoginSettingSeq, loginType, isEnforced, List.of());
        }

        var protocolType = superAdminLoginSettingRequestConverter.toProtocolType(loginType);
        var configs = ssoProviderFindService.findAllGlobalDetailsByProtocol(protocolType)
                                            .stream()
                                            .map(superAdminLoginSettingRequestConverter::toConfigResponse)
                                            .toList();

        if (configs.isEmpty()) {
            return new SuperAdminLoginSettingResponse(superAdminLoginSettingSeq, loginType, isEnforced, List.of());
        }

        return new SuperAdminLoginSettingResponse(superAdminLoginSettingSeq, loginType, isEnforced, configs);
    }

    @AuditLog
    @Transactional
    public void upsert(SuperAdminLoginSettingSaveRequest request) {

        boolean oldEnforced = settingAuditLookupService.isGlobalLoginEnforced();
        boolean newEnforced = Boolean.TRUE.equals(request.ie());

        if (!newEnforced) {
            superAdminLoginSettingService.upsertEnforce(request.ie());
            settingAuditService.recordGlobalLoginPolicyChangeIfNeeded(oldEnforced, newEnforced);
            return;
        }

        var loginType = request.l();
        var superAdminLoginSettingUpsertDto = superAdminLoginSettingRequestConverter.toEnforcedUpsertDto(request);

        superAdminLoginSettingService.upsert(superAdminLoginSettingUpsertDto);
        settingAuditService.recordGlobalLoginPolicyChangeIfNeeded(oldEnforced, newEnforced);

        if (loginType == LoginType.LOCAL) {
            return;
        }

        var protocolType = superAdminLoginSettingRequestConverter.toProtocolType(loginType);
        var upsertDtoList = superAdminLoginSettingRequestConverter.toSsoProviderUpsertDtoList(request.ps(), protocolType);

        switch (protocolType) {
            case JWT  -> ssoProviderService.syncGlobalJwt(upsertDtoList.get(0));
            case OIDC -> ssoProviderService.syncGlobalOidcList(upsertDtoList);
            default   -> throw ApiRuntimeException.type(SsoProtocolErrorCode.UNSUPPORTED_PROTOCOL);
        }
    }
}
