package com.thinkfree.drive.auth.token.blacklist.store;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistEntry;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenQueryRepository;
import com.thinkfree.drive.global.redis.ConditionalOnDriveRedis;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JWT 블랙리스트 Redis 사본 — 키 {@code <prefix>bl:<tokenHash>}(TTL = 토큰 남은 만료), 적재 완료 표시 {@code <prefix>bl-loaded}(만료 없음).
 *
 * <p>"Redis 에 없음" 을 "블랙리스트 아님" 으로 믿으려면 사본이 DB 와 맞아야 한다. 어긋나는 경우와 대응(설계 §3-2):
 * <ul>
 *   <li><b>Redis 장애</b>: 조회 오류 → null → 호출부가 DB 로 확인</li>
 *   <li><b>Redis 재시작으로 데이터 유실</b>: Lettuce 가 알아서 다시 붙어 앱은 모른다. 그래서 표시 키가 없으면 사본을 믿지 않고(null)
 *       뒤에서 DB 의 미만료 항목을 다시 싣는다. 키를 모두 실은 뒤에 표시 키를 둔다</li>
 *   <li><b>로그아웃 SET 한 번 실패</b>: 표시 키를 지워 다시 실을 때까지 DB 로 확인하게 한다(지우기도 실패하면 1분 주기 동기화가 메운다)</li>
 *   <li><b>재적재 도중 SET 실패</b>: 재적재가 DB 를 읽은 뒤의 로그아웃은 그 목록에 없다. 실패 시 세대 키 {@code <prefix>bl-epoch} 와
 *       표시 키를 한 번의 {@code DEL} 로 지우고, 재적재는 DB 를 읽기 전에 본 세대가 그대로 있을 때만 표시 키를 둔다(WAS 여러 대에서도).
 *       Redis 재시작으로 세대 키가 사라져도 표시 키를 두지 않는다</li>
 *   <li><b>메모리 부족(noeviction OOM)</b>: SET 은 거절되지만 {@code DEL} 은 허용된다 — 그래서 무효화는 DEL 만 쓴다.
 *       표시 키를 두는 스크립트는 쓰기라 OOM 동안 실패하고, 그동안은 DB 로 확인한다</li>
 *   <li>메모리가 차서 키가 밀려나지 않게 Redis 는 {@code maxmemory-policy noeviction}(기동 시 확인 — RedisConnectionMonitor)</li>
 * </ul>
 * 조회는 표시 키와 토큰 키를 {@code MGET} 한 번으로 읽는다.
 */
@Slf4j
@Component
@ConditionalOnDriveRedis
public class RedisBlacklistStore implements BlacklistStore {

    static final String VALUE = "1";
    /** 표시 키가 없을 때 재적재를 너무 자주 하지 않게 — 그 사이 조회는 DB 로 */
    static final Duration MIN_RELOAD_INTERVAL = Duration.ofSeconds(5);

    /** 세대가 재적재 시작 때 본 값과 같을 때만 표시 키를 둔다. KEYS[1]=세대, KEYS[2]=표시, ARGV[1]=본 세대 */
    private static final RedisScript<Long> MARK_LOADED_IF_SAME_EPOCH = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('SET', KEYS[2], '1') return 1 end return 0",
            Long.class);

    private final StringRedisTemplate redis;
    private final BlacklistTokenQueryRepository queryRepository;
    private final DriveRedisProperty property;
    private final Clock clock;
    private final String loadedKey;
    private final String epochKey;
    private final Counter fallbacks;
    private final Counter writeFailures;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "blacklist-redis-reload");
        t.setDaemon(true);
        return t;
    });
    private volatile Instant lastReloadRequest = Instant.EPOCH;

    @Autowired
    public RedisBlacklistStore(StringRedisTemplate redis, BlacklistTokenQueryRepository queryRepository,
                               DriveRedisProperty property, MeterRegistry meterRegistry) {
        this(redis, queryRepository, property, meterRegistry, Clock.systemUTC());
    }

    RedisBlacklistStore(StringRedisTemplate redis, BlacklistTokenQueryRepository queryRepository,
                        DriveRedisProperty property, MeterRegistry meterRegistry, Clock clock) {
        this.redis = redis;
        this.queryRepository = queryRepository;
        this.property = property;
        this.clock = clock;
        this.loadedKey = property.key("bl-loaded");
        this.epochKey = property.key("bl-epoch");
        this.fallbacks = Counter.builder("drive.blacklist.redis.fallback")
                .description("블랙리스트를 Redis 사본으로 답하지 못하고 DB 로 확인한 횟수(장애·미적재)")
                .register(meterRegistry);
        this.writeFailures = Counter.builder("drive.blacklist.redis.write.failures")
                .description("로그아웃 토큰을 Redis 사본에 넣지 못한 횟수")
                .register(meterRegistry);
    }

    String tokenKey(String tokenHash) {
        return property.key("bl", tokenHash);
    }

    @Override
    public Boolean contains(String tokenHash) {
        try {
            List<String> values = redis.opsForValue().multiGet(List.of(loadedKey, tokenKey(tokenHash)));
            if (values == null || values.size() != 2) {
                fallbacks.increment();
                return null;
            }
            if (values.get(0) == null) {
                fallbacks.increment();
                requestReload();
                return null;
            }
            return values.get(1) != null;
        } catch (Exception e) {
            fallbacks.increment();
            log.debug("[Blacklist] Redis 조회 실패 — DB 로 확인", e);
            return null;
        }
    }

    @Override
    public void add(String tokenHash, Instant expiresAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(tokenHash, expiresAt);
                }
            });
            return;
        }
        write(tokenHash, expiresAt);
    }

    private void write(String tokenHash, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return; // 이미 만료 — 서명 검증에서 거절된다
        }
        try {
            redis.opsForValue().set(tokenKey(tokenHash), VALUE, ttl);
        } catch (Exception e) {
            writeFailures.increment();
            log.warn("[Blacklist] Redis 사본 기록 실패 — 표시 키를 지워 다시 실을 때까지 DB 로 확인한다", e);
            try {
                // 여러 키 DEL 은 원자적이고 메모리 부족(noeviction)에서도 허용된다 — 세대가 사라지면 진행 중 재적재도 표시 키를 못 둔다
                redis.delete(List.of(epochKey, loadedKey));
            } catch (Exception ignored) {
                log.warn("[Blacklist] 표시 키 삭제도 실패 — 1분 주기 동기화까지 이 토큰이 Redis 사본에 없다");
            }
        }
    }

    /** 표시 키가 없을 때 — 뒤에서 한 번만, 너무 자주는 안 함 */
    void requestReload() {
        Instant now = clock.instant();
        if (now.isBefore(lastReloadRequest.plus(MIN_RELOAD_INTERVAL))) {
            return;
        }
        lastReloadRequest = now;
        try {
            reloadExecutor.execute(this::reload);
        } catch (Exception e) {
            log.debug("[Blacklist] 재적재 예약 실패", e);
        }
    }

    /**
     * DB 의 미만료 항목을 모두 싣고 마지막에 표시 키를 둔다. 여러 번 불러도 같다(멱등).
     * 1분 주기 동기화(BlacklistRedisSyncJob)와 표시 키가 없을 때 부른다.
     * @return 실은 항목 수, 실패하거나 이미 실는 중이면 -1
     */
    public int reload() {
        if (!reloading.compareAndSet(false, true)) {
            return -1;
        }
        try {
            // DB 를 읽기 전에 세대를 본다 — 이 뒤 커밋된 로그아웃의 SET 이 실패하면 세대가 바뀌어 표시 키를 두지 않는다
            redis.opsForValue().setIfAbsent(epochKey, UUID.randomUUID().toString());
            String epoch = redis.opsForValue().get(epochKey);
            if (epoch == null) {
                return -1;
            }
            Instant now = clock.instant();
            List<BlacklistEntry> entries = queryRepository.findAllNotExpired(now);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                RedisStringCommands strings = connection.stringCommands();
                for (BlacklistEntry entry : entries) {
                    long ttlMs = Duration.between(now, entry.expirationDate()).toMillis();
                    if (ttlMs > 0) {
                        strings.set(bytes(tokenKey(entry.tokenHash())), bytes(VALUE), Expiration.milliseconds(ttlMs),
                                RedisStringCommands.SetOption.upsert());
                    }
                }
                return null;
            });
            Long marked = redis.execute(MARK_LOADED_IF_SAME_EPOCH, List.of(epochKey, loadedKey), epoch);
            if (marked == null || marked != 1L) {
                log.info("[Blacklist] 적재 도중 사본 쓰기 실패(세대 변경) — 표시 키를 두지 않고 다음 재적재까지 DB 로 확인한다");
                return -1;
            }
            log.debug("[Blacklist] Redis 사본 적재 {}건", entries.size());
            return entries.size();
        } catch (Exception e) {
            log.warn("[Blacklist] Redis 사본 적재 실패 — 표시 키가 없는 동안 DB 로 확인한다: {}", e.getMessage());
            return -1;
        } finally {
            reloading.set(false);
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @PreDestroy
    void shutdown() {
        reloadExecutor.shutdownNow();
    }
}
