package com.thinkfree.drive.global.redis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Redis 연결 상태 — 기동 시 확인 로그와 지표 {@code drive_redis_up}(1/0).
 *
 * <p>Spring 의 Redis health 지표({@code management.health.redis})는 켜지 않는다. 켜면 Redis 가 죽었을 때 {@code /actuator/health} 가 DOWN 이 되어
 * 로드밸런서·k8s 가 WAS 를 빼 버린다 — Redis 장애가 전체 장애가 된다(설계 §2-3 과 반대). 상태는 이 지표로 보고 경보를 건다.
 *
 * <p>기동 시 {@code maxmemory-policy} 가 {@code noeviction} 이 아니면 경고한다 — 메모리가 차면 키가 밀려나 JWT 블랙리스트 같은 "없으면 통과" 값이
 * 사라진다(설계 §3-2). 관리형 Redis 처럼 CONFIG 명령이 막혀 있으면 확인을 건너뛴다. 기동은 Redis 가 없어도 계속한다.
 */
@Slf4j
@Component
@ConditionalOnDriveRedis
public class RedisConnectionMonitor {

    static final String UP_METRIC = "drive.redis.up";
    static final String REQUIRED_EVICTION_POLICY = "noeviction";

    private final RedisConnectionFactory connectionFactory;

    public RedisConnectionMonitor(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        this.connectionFactory = connectionFactory;
        Gauge.builder(UP_METRIC, this, m -> m.ping() ? 1 : 0)
                .description("Redis PING 성공 여부(1/0) — 수집할 때마다 확인")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        if (!ping()) {
            log.warn("[Redis] 연결 실패 — drive.redis.enabled=true 지만 Redis 에 닿지 않는다. Redis 를 쓰는 기능은 각자 장애 시 동작으로 계속한다");
            return;
        }
        log.info("[Redis] 연결 확인");
        String policy = evictionPolicy();
        if (policy == null) {
            log.info("[Redis] maxmemory-policy 를 확인할 수 없다(CONFIG 명령 제한) — noeviction 인지 직접 확인할 것");
        } else if (!REQUIRED_EVICTION_POLICY.equals(policy)) {
            log.warn("[Redis] maxmemory-policy={} — noeviction 이 아니면 메모리가 찰 때 키가 밀려나 로그아웃 토큰이 통과할 수 있다", policy);
        }
    }

    boolean ping() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception e) {
            log.debug("[Redis] PING 실패", e);
            return false;
        }
    }

    /** @return maxmemory-policy, 확인할 수 없으면 null */
    String evictionPolicy() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties config = connection.serverCommands().getConfig("maxmemory-policy");
            return config == null ? null : config.getProperty("maxmemory-policy");
        } catch (Exception e) {
            log.debug("[Redis] CONFIG GET maxmemory-policy 실패", e);
            return null;
        }
    }
}
