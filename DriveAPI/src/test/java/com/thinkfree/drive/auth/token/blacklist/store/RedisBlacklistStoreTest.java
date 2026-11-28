package com.thinkfree.drive.auth.token.blacklist.store;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistEntry;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.repository.BlacklistTokenQueryRepository;
import com.thinkfree.drive.auth.token.blacklist.service.impl.DefaultBlacklistTokenFindService;
import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 블랙리스트 Redis 사본 — 실제 Redis(Testcontainers) + DB 원본은 목.
 * 끝 기준(todo B): Redis 정지·재시작(데이터 유실)·SET 실패 각각에서 로그아웃 토큰이 거절됨.
 */
@DisplayName("JWT 블랙리스트 Redis 사본")
class RedisBlacklistStoreTest {

    static GenericContainer<?> redisServer;
    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    final DriveRedisProperty property = new DriveRedisProperty(true, "bt:");
    final BlacklistTokenQueryRepository db = mock(BlacklistTokenQueryRepository.class);
    RedisBlacklistStore store;
    DefaultBlacklistTokenFindService findService;

    static final Instant IN_ONE_HOUR = Instant.now().plus(Duration.ofHours(1));

    @BeforeAll
    static void startRedis() {
        redisServer = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
        redisServer.start();
        connectionFactory = new LettuceConnectionFactory(redisServer.getHost(), redisServer.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        store = new RedisBlacklistStore(redis, db, property, new SimpleMeterRegistry());
        findService = new DefaultBlacklistTokenFindService(db, store);
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        store.shutdown();
    }

    private boolean isBlacklisted(String hash) {
        return findService.existsByTokenHash(new BlacklistFindQuery(hash));
    }

    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    @Test
    @DisplayName("적재 뒤에는 Redis 로만 답한다 — DB 를 조회하지 않음, 블랙리스트 아님도 Redis 로")
    void answersFromRedisAfterLoad() {
        given(db.findAllNotExpired(any())).willReturn(List.of(new BlacklistEntry("h1", IN_ONE_HOUR)));
        assertThat(store.reload()).isEqualTo(1);

        assertThat(isBlacklisted("h1")).isTrue();
        assertThat(isBlacklisted("other")).isFalse();
        verify(db, never()).existsByTokenHash(any());
        assertThat(redis.getExpire("bt:bl:h1")).as("TTL = 토큰 남은 만료").isBetween(3500L, 3600L);
    }

    @Test
    @DisplayName("로그아웃 기록은 커밋 뒤에 사본에 들어간다, 롤백이면 안 들어간다")
    void addAfterCommit() {
        given(db.findAllNotExpired(any())).willReturn(List.of());
        store.reload();

        TransactionSynchronizationManager.initSynchronization();
        store.add("h2", IN_ONE_HOUR);
        assertThat(redis.hasKey("bt:bl:h2")).as("커밋 전").isFalse();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(isBlacklisted("h2")).isTrue();

        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        store.add("h3", IN_ONE_HOUR);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(redis.hasKey("bt:bl:h3")).as("롤백").isFalse();
    }

    @Test
    @DisplayName("Redis 재시작으로 데이터 유실 — 표시 키가 없으면 DB 로 확인해 거절하고, 뒤에서 다시 싣는다")
    void dataLossFallsBackToDbAndReloads() throws Exception {
        given(db.findAllNotExpired(any())).willReturn(List.of(new BlacklistEntry("h1", IN_ONE_HOUR)));
        store.reload();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll(); // 재시작 + 데이터 유실

        when(db.existsByTokenHash(new BlacklistFindQuery("h1"))).thenReturn(true);
        assertThat(isBlacklisted("h1")).as("Redis 에 없지만 DB 로 거절").isTrue();
        verify(db).existsByTokenHash(new BlacklistFindQuery("h1"));

        assertThat(eventually(() -> Boolean.TRUE.equals(store.contains("h1")))).as("뒤에서 재적재").isTrue();
        assertThat(redis.hasKey("bt:bl-loaded")).isTrue();
    }

    /** opsForValue().set(k, v, ttl) 만 실패하고 나머지는 실제 Redis 로 가는 템플릿 */
    private static StringRedisTemplate failingTokenSet() {
        StringRedisTemplate spy = org.mockito.Mockito.spy(redis);
        ValueOperations<String, String> real = redis.opsForValue();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class, org.mockito.AdditionalAnswers.delegatesTo(real));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("blip")).when(ops).set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.doReturn(ops).when(spy).opsForValue();
        return spy;
    }

    @Test
    @DisplayName("SET 실패 — 표시 키를 지워 DB 로 확인하게 한다(다른 토큰도 사본을 믿지 않음)")
    void writeFailureDropsLoadedMarker() {
        given(db.findAllNotExpired(any())).willReturn(List.of());
        store.reload();

        RedisBlacklistStore flaky = new RedisBlacklistStore(failingTokenSet(), db, property, new SimpleMeterRegistry());

        flaky.add("h4", IN_ONE_HOUR);
        assertThat(redis.hasKey("bt:bl-loaded")).as("표시 키 삭제").isFalse();

        when(db.existsByTokenHash(new BlacklistFindQuery("h4"))).thenReturn(true);
        assertThat(isBlacklisted("h4")).as("Redis 사본엔 없지만 DB 로 거절").isTrue();
        flaky.shutdown();
    }

    @Test
    @DisplayName("재적재가 DB 를 읽은 뒤 다른 WAS 의 로그아웃 SET 이 실패하면 표시 키를 두지 않는다 — 다음 재적재에서 반영")
    void writeFailureDuringReloadKeepsDbFallback() throws InterruptedException {
        RedisBlacklistStore otherWas = new RedisBlacklistStore(failingTokenSet(), db, property, new SimpleMeterRegistry());
        java.util.concurrent.atomic.AtomicBoolean firstRead = new java.util.concurrent.atomic.AtomicBoolean(true);
        given(db.findAllNotExpired(any())).willAnswer(inv -> {
            if (firstRead.getAndSet(false)) {
                otherWas.add("h7", IN_ONE_HOUR); // DB 목록을 읽은 직후 커밋된 로그아웃, Redis SET 실패
                return List.of();
            }
            return List.of(new BlacklistEntry("h7", IN_ONE_HOUR)); // 그 뒤 읽기엔 들어 있다
        });

        assertThat(store.reload()).as("세대가 바뀌어 완료 표시 안 함").isEqualTo(-1);
        assertThat(redis.hasKey("bt:bl-loaded")).isFalse();
        when(db.existsByTokenHash(new BlacklistFindQuery("h7"))).thenReturn(true);
        assertThat(isBlacklisted("h7")).as("DB 로 거절").isTrue();

        // 다음 재적재(contains() 가 뒤에서 띄운 것과 겹치면 -1 이라 끝날 때까지)
        assertThat(eventually(() -> store.reload() == 1 || Boolean.TRUE.equals(store.contains("h7")))).as("다음 재적재").isTrue();
        assertThat(store.contains("h7")).isTrue();
        otherWas.shutdown();
    }

    @Test
    @DisplayName("적재 도중 Redis 재시작(세대 키 유실) — 표시 키를 두지 않는다")
    void dataLossDuringReloadDoesNotMark() {
        given(db.findAllNotExpired(any())).willAnswer(inv -> {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
            return List.of();
        });
        assertThat(store.reload()).isEqualTo(-1);
        assertThat(redis.hasKey("bt:bl-loaded")).isFalse();
    }

    @Test
    @DisplayName("Redis 정지 — 조회 오류를 DB 로 넘겨 거절한다, 예외 없음")
    void redisDownFallsBackToDb() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        dead.start();
        try {
            RedisBlacklistStore down = new RedisBlacklistStore(new StringRedisTemplate(dead), db, property, new SimpleMeterRegistry());
            DefaultBlacklistTokenFindService service = new DefaultBlacklistTokenFindService(db, down);
            when(db.existsByTokenHash(new BlacklistFindQuery("h5"))).thenReturn(true);

            assertThat(service.existsByTokenHash(new BlacklistFindQuery("h5"))).isTrue();
            down.add("h6", IN_ONE_HOUR); // 예외 없이 넘어간다
            assertThat(down.reload()).isEqualTo(-1);
            down.shutdown();
        } finally {
            dead.destroy();
        }
    }

    @Test
    @DisplayName("이미 만료된 토큰은 싣지 않는다")
    void expiredNotWritten() {
        given(db.findAllNotExpired(any())).willReturn(List.of());
        store.reload();
        store.add("old", Instant.now().minusSeconds(1));
        assertThat(redis.hasKey("bt:bl:old")).isFalse();
    }
}
