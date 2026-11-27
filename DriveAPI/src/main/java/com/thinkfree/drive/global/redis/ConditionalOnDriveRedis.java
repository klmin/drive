package com.thinkfree.drive.global.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code drive.redis.enabled=true} 일 때만 등록되는 빈 — Redis 를 쓰는 구현에 붙인다.
 * 꺼져 있을 때 쓸 구현(DB·로컬)은 {@code @ConditionalOnProperty(name = "drive.redis.enabled", havingValue = "false", matchIfMissing = true)}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "drive.redis.enabled", havingValue = "true")
public @interface ConditionalOnDriveRedis {
}
