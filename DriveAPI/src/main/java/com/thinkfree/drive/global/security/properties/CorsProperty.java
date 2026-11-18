package com.thinkfree.drive.global.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("cors")
public record CorsProperty
(
        List<String> allowedOrigins
)
{
}
