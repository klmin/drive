package com.thinkfree.drive.tenant.properties;

import com.thinkfree.drive.tenant.enums.TenantResolutionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;


@ConfigurationProperties("tenant")
public record TenantProperty
(
    long cacheTtlSeconds,
    int cacheMaxSize,
    boolean devFallbackEnabled,
    Integer devDefaultTenantSeq,
    /** 테넌트 판별 방식. 미지정 시 PATH. */
    TenantResolutionMode resolutionMode
)
{
    @ConstructorBinding
    public TenantProperty {
        if (resolutionMode == null) {
            resolutionMode = TenantResolutionMode.PATH;
        }
    }

    /** 기존 4인자 생성자 호환 — 기본 모드(PATH). */
    public TenantProperty(long cacheTtlSeconds, int cacheMaxSize, boolean devFallbackEnabled, Integer devDefaultTenantSeq) {
        this(cacheTtlSeconds, cacheMaxSize, devFallbackEnabled, devDefaultTenantSeq, TenantResolutionMode.PATH);
    }
}
