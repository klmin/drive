package com.thinkfree.drive.branding.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("branding")
public record BrandingProperty(
        Defaults defaults
) {
    public record Defaults
    (
        String tabTitle,
        String serviceName
    )
    {
    }
}
