package com.thinkfree.drive.app.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.front")
public record AppFrontProperty
(
    String domain,
    String baseUrl,
    String loginPath,
    Integer port
)
{
}
