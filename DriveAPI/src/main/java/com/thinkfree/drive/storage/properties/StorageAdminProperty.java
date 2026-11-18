package com.thinkfree.drive.storage.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 스토리지 관리(용량 조회) API 접속 정보({@code storage.admin.*}).
 * 현재는 Ceph 대시보드가 사용하며, RustFS admin API 등 다른 종류의 관리 API도 같은 블록을 쓴다.
 * 용량 조회가 없는 종류(AWS S3 등)에서는 비워둘 수 있다.
 */
@ConfigurationProperties("storage.admin")
public record StorageAdminProperty
(
    String url,
    String username,
    String password,
    Duration refreshThreshold
)
{
}
