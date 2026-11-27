package com.thinkfree.drive.global.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Redis 사용 스위치 — docs/design/scaling-strategy.md §2-3.
 *
 * <p>Redis 는 선택 구성요소다. 꺼져 있으면(기본) Redis 를 쓰는 기능은 각자 DB·로컬 방식으로 동작하고, 어떤 코드도 Redis 에 연결하지 않는다.
 * 켜져 있어도 Redis 장애가 서비스 중단이 되면 안 된다 — 기능마다 장애 시 동작(DB 로 조회, 통과, TTL 로 반영)을 따로 정한다.
 * 연결 정보는 Spring 표준 {@code spring.data.redis.*}(환경변수 {@code DRIVE_REDIS_HOST} 등).
 *
 * @param enabled   Redis 를 쓰는지. {@code DRIVE_REDIS_ENABLED}
 * @param keyPrefix 모든 키 앞에 붙이는 접두어 — 여러 설치가 Redis 하나를 같이 쓸 때 키가 섞이지 않게. 테넌트 구분은 각 기능의 키에 tenantSeq 로 넣는다
 */
@ConfigurationProperties("drive.redis")
public record DriveRedisProperty(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("drive:") String keyPrefix
) {

    public DriveRedisProperty {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "drive:";
        }
    }

    /** 접두어를 붙인 키 — 예: {@code key("bl", hash)} → {@code drive:bl:hash} */
    public String key(String... parts) {
        return keyPrefix + String.join(":", parts);
    }
}
