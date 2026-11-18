package com.thinkfree.drive.api.v1.admin.branding.response;

public record BrandingSettingResponse
(
    Integer brandingSeq,
    String tabTitle,
    String serviceName,
    boolean useDefault
)
{
}
