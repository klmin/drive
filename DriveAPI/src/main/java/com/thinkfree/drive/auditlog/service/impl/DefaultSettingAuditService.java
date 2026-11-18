package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.auditlog.support.ListDeltaAuditSupport;
import com.thinkfree.drive.auditlog.support.SettingAuditSupport;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultSettingAuditService implements SettingAuditService {

    private final TenantFindService tenantFindService;
    private final ObjectMapperUtil objectMapperUtil;

    @Override
    public void recordLoginMethodChangeIfNeeded(LoginType before, LoginType after) {
        if (before == after) {
            return;
        }
        SettingAuditSupport.recordUpdate("loginMethod", before.name(), after.name());
    }

    @Override
    public void recordExternalShareChangeIfNeeded(boolean before, boolean after) {
        if (before == after) {
            return;
        }
        SettingAuditSupport.recordUpdate("externalShare", toOnOff(before), toOnOff(after));
    }

    @Override
    public void recordGlobalLoginPolicyChangeIfNeeded(boolean oldEnforced, boolean newEnforced) {
        recordGlobalPolicyChangeIfNeeded("globalLoginPolicy", oldEnforced, newEnforced);
    }

    @Override
    public void recordGlobalSharePolicyChangeIfNeeded(boolean oldEnforced, boolean newEnforced) {
        recordGlobalPolicyChangeIfNeeded("globalSharePolicy", oldEnforced, newEnforced);
    }

    private void recordGlobalPolicyChangeIfNeeded(String objectName, boolean oldEnforced, boolean newEnforced) {
        if (oldEnforced == newEnforced) {
            return;
        }
        String before = oldEnforced ? "GLOBAL" : "TENANT";
        String after = newEnforced ? "GLOBAL" : "TENANT";
        for (Integer tenantSeq : tenantFindService.findSelfManagedTenantSeqs()) {
            SettingAuditSupport.recordUpdate(tenantSeq, objectName, before, after);
        }
    }

    @Override
    public void recordAllowedIpChangeIfNeeded(List<String> beforeIps, List<String> afterIps) {
        Map<String, List<String>> delta = ListDeltaAuditSupport.computeDelta(beforeIps, afterIps);
        if (delta.isEmpty()) {
            return;
        }
        SettingAuditSupport.recordAllowedIpChange(objectMapperUtil.writeValueAsString(delta));
    }

    private static String toOnOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
