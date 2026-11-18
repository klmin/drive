package com.thinkfree.drive.sso.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sso")
public record SsoProperties
(
    String frontendCallbackPath
    
)
{
}



