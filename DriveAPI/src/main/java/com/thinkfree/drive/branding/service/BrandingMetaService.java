package com.thinkfree.drive.branding.service;

import com.thinkfree.drive.branding.enums.BrandingImageSource;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.result.BrandingImageResult;

public interface BrandingMetaService {

    BrandingImageResult getImage(Integer tenantSeq, BrandingImageType type, BrandingImageSource source);
    /** 테넌트 무관 클래스패스 기본 이미지 — 테넌트가 특정되지 않은 요청(슈퍼어드민 포털)용. */
    BrandingImageResult getDefaultImage(BrandingImageType type);
    String findServiceNameOrDefault(int tenantSeq);
}
