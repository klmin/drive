package com.thinkfree.drive.api.v1.branding.usecase;

import com.thinkfree.drive.api.v1.branding.response.BrandingMetaResponse;
import com.thinkfree.drive.branding.properties.BrandingProperty;
import com.thinkfree.drive.branding.service.BrandingFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandingUseCase {

    private final BrandingFindService brandingFindService;
    private final BrandingProperty brandingProperty;
    private final LicenseService licenseService;

    /**
     * 테넌트가 특정되지 않은 요청(슈퍼어드민 포털 등, {@code SecurityPaths.TENANT_OPTIONAL})은 TenantContext 가 비어 있다 —
     * 테넌트 브랜딩·라이선스 플랜이 없으므로 yml 기본값만 돌려준다(plan·category null).
     */
    public BrandingMetaResponse getMeta() {

        var defaults = brandingProperty.defaults();
        Integer tenantSeq = TenantContext.get();
        if (tenantSeq == null) {
            return new BrandingMetaResponse(defaults.tabTitle(), defaults.serviceName(), null, null);
        }
        var plan = licenseService.getLicensePlan(tenantSeq);
        BrandingMetaResponse brandingMetaResponse = new BrandingMetaResponse(defaults.tabTitle(), defaults.serviceName(), plan.subCategory(), plan.category());
        
        try {
            var meta = brandingFindService.findMetaOrThrow(tenantSeq);
            if (!licenseService.isPaidWhiteLabel(tenantSeq)) {
                return brandingMetaResponse;
            }
            return new BrandingMetaResponse(meta.getTabTitle(), meta.getServiceName(), plan.subCategory(), plan.category());
        } catch (ApiRuntimeException e) {
            return brandingMetaResponse;
        }
    }
}
