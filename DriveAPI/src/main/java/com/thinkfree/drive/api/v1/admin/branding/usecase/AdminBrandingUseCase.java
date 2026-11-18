package com.thinkfree.drive.api.v1.admin.branding.usecase;

import com.thinkfree.drive.api.v1.admin.branding.converter.AdminBrandingConverter;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingImageRequest;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingUpsertRequest;
import com.thinkfree.drive.api.v1.admin.branding.response.BrandingSettingResponse;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.result.BrandingImageResult;
import com.thinkfree.drive.branding.service.BrandingFindService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.branding.service.BrandingService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBrandingUseCase {

    private final BrandingService        brandingService;
    private final BrandingFindService    brandingFindService;
    private final BrandingMetaService brandingMetaService;
    private final AdminBrandingConverter adminBrandingConverter;
    private final LicenseService         licenseService;

    public BrandingSettingResponse get() {
        var data = brandingFindService.findMetaOrThrow(TenantContext.getOrThrow());
        return adminBrandingConverter.toResponse(data);
    }

    public void upsert(BrandingUpsertRequest request) {
        int tenantSeq = TenantContext.getOrThrow();
        DriveAssert.throwIfFalse(licenseService.isPaidWhiteLabel(tenantSeq), BrandingErrorCode.WHITE_LABEL_NOT_LICENSED);
        var dto = adminBrandingConverter.toUpsertDto(tenantSeq, request);
        brandingService.upsert(dto);
    }

    public BrandingImageResult getImage(BrandingImageType type, BrandingImageRequest request) {
        return brandingMetaService.getImage(TenantContext.getOrThrow(), type, request.source());
    }
    
}
