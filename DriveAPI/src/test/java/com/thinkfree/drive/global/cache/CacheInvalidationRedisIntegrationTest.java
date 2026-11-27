package com.thinkfree.drive.global.cache;

import com.thinkfree.drive.accessrestriction.cache.AccessPolicyCache;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import com.thinkfree.drive.user.enums.RoleType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAS 2대를 흉내 — 같은 Redis 를 보는 애플리케이션 컨텍스트 두 개. 한쪽에서 정책을 비우면 다른 쪽 로컬 캐시도 1초 안에 비워지는지.
 * Redis 는 Testcontainers(도커 필요).
 */
@DisplayName("캐시 무효화 pub/sub — 실제 Redis, 컨텍스트 2개")
class CacheInvalidationRedisIntegrationTest {

    static GenericContainer<?> redis;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        if (redis != null) {
            redis.stop();
        }
    }

    @Configuration
    @EnableConfigurationProperties(DriveRedisProperty.class)
    @Import({RedisCacheInvalidationConfig.class, RedisCacheInvalidationBroadcaster.class, NoopCacheInvalidationBroadcaster.class, AccessPolicyCache.class})
    static class Was {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private ApplicationContextRunner was(String host, int port) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(Was.class)
                .withPropertyValues(
                        "drive.redis.enabled=true",
                        "drive.redis.key-prefix=it:",
                        "spring.data.redis.host=" + host,
                        "spring.data.redis.port=" + port,
                        "spring.data.redis.timeout=500ms");
    }

    /** 캐시에 담고, 로더가 불린 횟수를 센다 */
    private static void load(AccessPolicyCache cache, AtomicInteger loads) {
        cache.get(3, RoleType.USER, () -> {
            loads.incrementAndGet();
            return new AccessPolicyCache.Policy(true, List.of("10.0.0.0/24"), List.of());
        });
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    @Test
    @DisplayName("한 WAS 의 무효화가 다른 WAS 캐시를 1초 안에 비운다")
    void invalidationReachesOtherWas() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);
        was(host, port).run(a -> was(host, port).run(b -> {
            assertThat(a).hasNotFailed();
            assertThat(b).hasNotFailed();
            AccessPolicyCache cacheA = a.getBean(AccessPolicyCache.class);
            AccessPolicyCache cacheB = b.getBean(AccessPolicyCache.class);
            assertThat(a.getBean(CacheInvalidationBroadcaster.class)).isInstanceOf(RedisCacheInvalidationBroadcaster.class);
            waitForSubscription(a);
            waitForSubscription(b);

            AtomicInteger loadsB = new AtomicInteger();
            load(cacheB, loadsB);
            load(cacheB, loadsB);
            assertThat(loadsB).as("B 는 캐시에서 읽는다").hasValue(1);

            cacheA.invalidate(3); // 트랜잭션 밖 — 바로 PUBLISH

            assertThat(waitUntil(() -> {
                load(cacheB, loadsB);
                return loadsB.get() == 2;
            }, Duration.ofSeconds(1))).as("B 캐시가 1초 안에 비워져 다시 읽음").isTrue();

            // 다른 테넌트는 그대로
            AtomicInteger loadsOther = new AtomicInteger();
            cacheB.get(4, RoleType.USER, () -> {
                loadsOther.incrementAndGet();
                return new AccessPolicyCache.Policy(false, List.of(), List.of());
            });
            cacheA.invalidate(3);
            Thread.sleep(200);
            cacheB.get(4, RoleType.USER, () -> {
                loadsOther.incrementAndGet();
                return new AccessPolicyCache.Policy(false, List.of(), List.of());
            });
            assertThat(loadsOther).as("테넌트 4 는 비워지지 않음").hasValue(1);
        }));
    }

    @Test
    @DisplayName("Redis 에 닿지 않아도 기동하고, 무효화는 예외 없이 로컬만 비운다")
    void startsWithoutRedis() {
        was("127.0.0.1", 1).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            AccessPolicyCache cache = ctx.getBean(AccessPolicyCache.class);
            AtomicInteger loads = new AtomicInteger();
            load(cache, loads);
            cache.invalidate(3);
            load(cache, loads);
            assertThat(loads).hasValue(2);
        });
    }

    /** 구독이 붙기 전에 보낸 메시지는 받지 못한다(pub/sub 은 저장 안 함) — 테스트에서는 붙을 때까지 기다린다 */
    private static void waitForSubscription(ConfigurableApplicationContext ctx) throws InterruptedException {
        var container = ctx.getBean(org.springframework.data.redis.listener.RedisMessageListenerContainer.class);
        assertThat(waitUntil(container::isListening, Duration.ofSeconds(5))).as("구독 시작").isTrue();
    }
}
