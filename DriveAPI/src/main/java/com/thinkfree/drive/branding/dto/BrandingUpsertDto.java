package com.thinkfree.drive.branding.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BrandingUpsertDto {
    private Integer tenantSeq;
    private String tabTitle;
    private String serviceName;
    private boolean useDefault;
    private byte[] faviconData;
    private String faviconContentType;
    private byte[] logoIconData;
    private String logoIconContentType;
    private byte[] logoImageData;
    private String logoImageContentType;
    private byte[] emailLogoData;
    private String emailLogoContentType;
}
