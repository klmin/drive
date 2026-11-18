package com.thinkfree.drive.api.v1.admin.accessrestriction.usecase;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedIpsUpdateRequest;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessAllowedIpUseCase {

    private final AccessAllowedIpService accessAllowedIpService;
    private final SettingAuditService settingAuditService;

    @AuditLog
    @TenantFeatureRequired(TenantFeature.ACCESS_RESTRICTION)
    public void putAllowedIps(AllowedIpsUpdateRequest request, Integer requestUserSeq) {
        Integer tenantSeq = TenantContext.getOrThrow();
        var beforeIps = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.USER);

        accessAllowedIpService.replaceAllowedIps(tenantSeq, RoleType.USER, requestUserSeq, request.ips());

        settingAuditService.recordAllowedIpChangeIfNeeded(beforeIps, request.ips());
    }
}
