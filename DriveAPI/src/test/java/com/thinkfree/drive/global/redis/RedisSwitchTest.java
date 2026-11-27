package com.thinkfree.drive.global.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Redis 스위치(drive.redis.enabled)·연결 모니터")
class RedisSwitchTest {

    @Configuration
    @EnableConfigurationProperties(DriveRedisProperty.class)
    @Import(RedisConnectionMonitor.class)
    static class Config {
        static final RedisConnectionFactory FACTORY = mock(RedisConnectionFactory.class);

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return FACTORY;
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @BeforeEach
    void resetFactory() {
        org.mockito.Mockito.reset(Config.FACTORY);
    }

    @Test
    @DisplayName("기본(꺼짐) — Redis 모니터가 없고 연결을 시도하지 않는다")
    void disabledByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(RedisConnectionMonitor.class);
            assertThat(ctx.getBean(DriveRedisProperty.class).enabled()).isFalse();
            assertThat(ctx.getBean(MeterRegistry.class).find(RedisConnectionMonitor.UP_METRIC).gauge()).isNull();
        });
        verify(Config.FACTORY, never()).getConnection();
    }

    @Test
    @DisplayName("켜짐 — 모니터가 등록되고 drive_redis_up 이 PING 결과를 따른다")
    void enabledRegistersMonitor() {
        RedisConnection connection = mock(RedisConnection.class);
        given(Config.FACTORY.getConnection()).willReturn(connection);
        given(connection.ping()).willReturn("PONG");

        runner.withPropertyValues("drive.redis.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(RedisConnectionMonitor.class);
            MeterRegistry registry = ctx.getBean(MeterRegistry.class);
            assertThat(registry.get(RedisConnectionMonitor.UP_METRIC).gauge().value()).isEqualTo(1.0);

            given(connection.ping()).willThrow(new RedisConnectionFailureException("down"));
            assertThat(registry.get(RedisConnectionMonitor.UP_METRIC).gauge().value()).isEqualTo(0.0);
        });
    }

    @Test
    @DisplayName("기동 확인 — Redis 가 없거나 CONFIG 가 막혀도 예외 없이 넘어간다")
    void startupCheckNeverFails() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnectionMonitor monitor = new RedisConnectionMonitor(factory, new SimpleMeterRegistry());

        given(factory.getConnection()).willThrow(new RedisConnectionFailureException("down"));
        assertThatNoException().isThrownBy(monitor::checkOnStartup);

        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands server = mock(RedisServerCommands.class);
        org.mockito.Mockito.reset(factory);
        given(factory.getConnection()).willReturn(connection);
        given(connection.ping()).willReturn("PONG");
        given(connection.serverCommands()).willReturn(server);
        given(server.getConfig("maxmemory-policy")).willThrow(new IllegalStateException("CONFIG disabled"));
        assertThat(monitor.evictionPolicy()).isNull();
        assertThatNoException().isThrownBy(monitor::checkOnStartup);

        Properties props = new Properties();
        props.setProperty("maxmemory-policy", "allkeys-lru");
        org.mockito.Mockito.reset(server);
        given(server.getConfig("maxmemory-policy")).willReturn(props);
        assertThat(monitor.evictionPolicy()).isEqualTo("allkeys-lru");
        assertThatNoException().isThrownBy(monitor::checkOnStartup);
    }

    @Test
    @DisplayName("키 접두어 — 기본 drive:, 빈 값이면 기본으로")
    void keyPrefix() {
        assertThat(new DriveRedisProperty(true, "drive:").key("bl", "abc")).isEqualTo("drive:bl:abc");
        assertThat(new DriveRedisProperty(true, "site-a:").key("cache-invalidate")).isEqualTo("site-a:cache-invalidate");
        assertThat(new DriveRedisProperty(true, " ").keyPrefix()).isEqualTo("drive:");
        runner.withPropertyValues("drive.redis.key-prefix=site-b:")
                .run(ctx -> assertThat(ctx.getBean(DriveRedisProperty.class).key("x")).isEqualTo("site-b:x"));
    }
}
