package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.data.BrandingImageData;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.enums.BrandingImageType;

public interface BrandingFindService {
    BrandingMetaData findMetaOrThrow(int tenantSeq);
    BrandingMetaData findMeta(int tenantSeq);
    BrandingImageData findBrandingImage(int tenantSeq, BrandingImageType brandingImageType);
}
