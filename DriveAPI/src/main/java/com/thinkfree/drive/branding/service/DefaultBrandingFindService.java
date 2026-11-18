package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.converter.BrandingConverter;
import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.repository.BrandingQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultBrandingFindService implements BrandingFindService {

    private final BrandingConverter   brandingConverter;
    private final BrandingQueryRepository  brandingQueryRepository;

    @Override
    public BrandingMetaData findMetaOrThrow(int tenantSeq) {
        var meta = findMeta(tenantSeq);
        DriveAssert.throwIfNull(meta, BrandingErrorCode.BRANDING_NOT_FOUND);
        return meta;
    }

    @Override
    public BrandingMetaData findMeta(int tenantSeq) {
        return brandingQueryRepository.findMeta(tenantSeq);
    }

    @Override
    public BrandingImageData findBrandingImage(int tenantSeq, BrandingImageType brandingImageType) {
        var query = brandingConverter.toBrandingImageFindQuery(tenantSeq, brandingImageType);
        BrandingImageData data = brandingQueryRepository.findBrandingImage(query);
        DriveAssert.throwIfNull(data, BrandingErrorCode.BRANDING_NOT_FOUND);
        return data;
    }
}
