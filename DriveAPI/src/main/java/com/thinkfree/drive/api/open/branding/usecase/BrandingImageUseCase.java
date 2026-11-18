package com.thinkfree.drive.api.open.branding.usecase;

import com.thinkfree.drive.api.open.branding.converter.BrandingImageResponseConverter;
import com.thinkfree.drive.api.open.branding.response.BrandingImageResponse;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.enums.BrandingImageSource;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.service.BrandingFindService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BrandingImageUseCase {

    private static final String DEFAULT_ETAG = "\"default\"";

    private final BrandingImageResponseConverter brandingImageResponseConverter;
    private final BrandingFindService  brandingFindService;
    private final BrandingMetaService brandingMetaService;
    private final LicenseService licenseService;

    /**
     * 테넌트가 특정되지 않은 요청(슈퍼어드민 포털 등, {@code SecurityPaths.TENANT_OPTIONAL})은 TenantContext 가 비어 있다 —
     * 테넌트 브랜딩이 없으므로 클래스패스 기본 이미지를 {@code "default"} ETag 로 돌려준다.
     */
    public BrandingImageResponse getImage(BrandingImageType type, String ifNoneMatch) {

        Integer tenantSeq = TenantContext.get();
        if (tenantSeq == null) {
            if (DEFAULT_ETAG.equals(ifNoneMatch)) {
                return BrandingImageResponse.notModified(DEFAULT_ETAG);
            }
            return brandingImageResponseConverter.toBrandingImageResponse(brandingMetaService.getDefaultImage(type), DEFAULT_ETAG);
        }

        try {
            
            var meta = brandingFindService.findMetaOrThrow(tenantSeq);
            var eTag = toETag(meta, licenseService.isPaidWhiteLabel(tenantSeq));

            if (eTag.equals(ifNoneMatch)) {
                return BrandingImageResponse.notModified(eTag);
            }
            var source = meta.isUseDefault()
                    ? BrandingImageSource.DEFAULT
                    : BrandingImageSource.CUSTOM;
            var result = brandingMetaService.getImage(tenantSeq, type, source);

            return brandingImageResponseConverter.toBrandingImageResponse(result, eTag);

        } catch (ApiRuntimeException e) {
            if (e.getCode() != BrandingErrorCode.BRANDING_NOT_FOUND) {
                throw e;
            }
            if (DEFAULT_ETAG.equals(ifNoneMatch)) {
                return BrandingImageResponse.notModified(DEFAULT_ETAG);
            }

            var result = brandingMetaService.getImage(tenantSeq, type, BrandingImageSource.DEFAULT);
            return brandingImageResponseConverter.toBrandingImageResponse(result, DEFAULT_ETAG);
        }
    }

    private String toETag(BrandingMetaData data, boolean isWhiteLabel) {
        Instant base = data.getUpdateDate() != null ? data.getUpdateDate() : data.getRegisterDate();
        long millis = base != null ? base.toEpochMilli() : 0L;
        return "\"" + millis + "_" + (isWhiteLabel ? 1 : 0) + "\"";
    }

}
