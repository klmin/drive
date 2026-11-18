package com.thinkfree.drive.resource.version.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("resource.versions")
public record ResourceVersionProperty
(
    @Min(value = 1, message = "Version limit must be at least 1")
    Integer limit
)
{
}
