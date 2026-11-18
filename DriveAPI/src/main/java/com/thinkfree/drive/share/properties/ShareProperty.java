package com.thinkfree.drive.share.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("share")
public record ShareProperty
(
        String contextPath
)
{
}
