package com.thinkfree.drive.weboffice.httpstorage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/** Only lifetimes are deployment-wide; Office credentials are tenant settings. */
@Getter
@Setter
@ConfigurationProperties("tfo.storage")
public class HttpStorageProperties {
    private Duration openTtl = Duration.ofSeconds(60);
    private Duration sessionTtl = Duration.ofHours(24);
}
