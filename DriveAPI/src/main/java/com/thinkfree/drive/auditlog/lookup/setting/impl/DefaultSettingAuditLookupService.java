package com.thinkfree.drive.auditlog.lookup.setting.impl;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.lookup.setting.SettingAuditLookupService;
import com.thinkfree.drive.share.setting.service.ShareSettingFindService;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultSettingAuditLookupService implements SettingAuditLookupService {

    private final TenantFindService tenantFindService;
    private final SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    private final ShareSettingFindService shareSettingFindService;

    @Override
    public LoginType findTenantLoginType(int tenantSeq) {
        return tenantFindService.findByTenantSeq(tenantSeq).getLoginType();
    }

    @Override
    public boolean isGlobalLoginEnforced() {
        var setting = superAdminLoginSettingFindService.find();
        return setting != null && Boolean.TRUE.equals(setting.isEnforced());
    }

    @Override
    public boolean isGlobalShareEnforced() {
        var global = shareSettingFindService.findGlobal();
        return global != null && Boolean.TRUE.equals(global.enforced());
    }
}
