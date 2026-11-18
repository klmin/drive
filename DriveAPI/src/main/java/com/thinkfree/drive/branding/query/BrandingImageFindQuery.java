package com.thinkfree.drive.branding.query;

import com.thinkfree.drive.branding.enums.BrandingImageType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BrandingImageFindQuery {
    private Integer tenantSeq;
    private BrandingImageType type;
}
