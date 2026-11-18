package com.thinkfree.drive.global.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Drive 를 iframe 으로 넣을 수 있는 origin(developers 등) — {@code frame-ancestors} 허용 목록. 비어 있으면 같은 origin 만 허용.
 */
@ConfigurationProperties("embed")
public record EmbedProperty
(
        List<String> allowedOrigins
)
{
    public EmbedProperty {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }
}
