package com.thinkfree.drive.storage.properties;

import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage")
public record StorageProperty
(
    StorageType type
)
{
}
