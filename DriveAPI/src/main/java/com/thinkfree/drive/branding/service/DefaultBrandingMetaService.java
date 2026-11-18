package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.enums.BrandingImageSource;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.properties.BrandingProperty;
import com.thinkfree.drive.branding.result.BrandingImageResult;
import com.thinkfree.drive.common.file.domain.FileKey;
import com.thinkfree.drive.common.file.provider.FileProvider;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.license.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class DefaultBrandingMetaService implements BrandingMetaService {

    private final BrandingFindService brandingFindService;
    private final FileProvider fileProvider;
    private final LicenseService licenseService;
    private final BrandingProperty brandingProperty;

    @Override
    public BrandingImageResult getImage(Integer tenantSeq, BrandingImageType type, BrandingImageSource source) {
        if(licenseService.isPaidWhiteLabel(tenantSeq)){
            return switch (source) {
                case CUSTOM  -> fromDb(tenantSeq, type);
                case DEFAULT -> fromClasspath(type);
            };
        }
        return fromClasspath(type);
    }

    @Override
    public BrandingImageResult getDefaultImage(BrandingImageType type) {
        return fromClasspath(type);
    }

    @Override
    public String findServiceNameOrDefault(int tenantSeq) {

        var meta = brandingFindService.findMeta(tenantSeq);
        var defaultServiceName = brandingProperty.defaults().serviceName();

        if(meta == null){
            return defaultServiceName;
        }

        return licenseService.isPaidWhiteLabel(tenantSeq) ? meta.getServiceName() : defaultServiceName;

    }

    private BrandingImageResult fromDb(Integer tenantSeq, BrandingImageType type) {
        var data = brandingFindService.findBrandingImage(tenantSeq, type);
        return new BrandingImageResult(data.getImageData(), data.getContentType());
    }

    private BrandingImageResult fromClasspath(BrandingImageType type) {
        try {
            var fileData = fileProvider.getFileData(new FileKey(type.getClasspath()));
            var bytes = fileData.content().readAllBytes();
            return new BrandingImageResult(bytes, fileData.contentType());
        } catch (IOException e) {
            throw new ApiRuntimeException(BrandingErrorCode.IMAGE_CONVERT_FAILED);
        }
    }
}
