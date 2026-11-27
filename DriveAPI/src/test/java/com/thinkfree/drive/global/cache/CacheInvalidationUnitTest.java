package com.thinkfree.drive.global.cache;

import com.thinkfree.drive.global.redis.DriveRedisProperty;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("캐시 무효화 방송·수신 단위")
class CacheInvalidationUnitTest {

    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    final DriveRedisProperty property = new DriveRedisProperty(true, "drive:");
    final RedisCacheInvalidationBroadcaster broadcaster = new RedisCacheInvalidationBroadcaster(redis, property, new SimpleMeterRegistry());

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void complete(int status) {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                sync.afterCommit();
            }
            sync.afterCompletion(status);
        }
    }

    @Test
    @DisplayName("트랜잭션 밖 — 바로 보낸다(채널 = 접두어 + cache-invalidate)")
    void publishesImmediatelyWithoutTransaction() {
        broadcaster.broadcast("access-policy", "3");
        verify(redis).convertAndSend("drive:cache-invalidate", broadcaster.payload("access-policy", "3"));
    }

    @Test
    @DisplayName("트랜잭션 안 — 커밋 뒤에만 보낸다")
    void publishesAfterCommitOnly() {
        TransactionSynchronizationManager.initSynchronization();
        broadcaster.broadcast("access-policy", "3");
        verifyNoInteractions(redis);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(redis).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("롤백이면 보내지 않는다")
    void noPublishOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        broadcaster.broadcast("access-policy", "3");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("Redis 장애 — 예외를 올리지 않는다(다른 WAS 는 TTL 로)")
    void publishFailureIsSwallowed() {
        willThrow(new RedisConnectionFailureException("down")).given(redis).convertAndSend(anyString(), anyString());
        assertThatNoException().isThrownBy(() -> broadcaster.broadcast("host-tenant", CacheInvalidationBroadcaster.ALL));
    }

    @Test
    @DisplayName("수신 — 이름으로 캐시를 찾아 비우고, 자기 메시지·모르는 이름·틀린 형식·삭제 실패는 넘어간다")
    void listenerDispatch() {
        LocallyInvalidatable a = mock(LocallyInvalidatable.class);
        when(a.cacheName()).thenReturn("access-policy");
        LocallyInvalidatable h = mock(LocallyInvalidatable.class);
        when(h.cacheName()).thenReturn("host-tenant");
        RedisCacheInvalidationListener listener = new RedisCacheInvalidationListener(List.of(a, h), "self");

        listener.handle("other-was:access-policy:3");
        verify(a).evictLocal("3");

        listener.handle("other-was:host-tenant:drive.example.com:8443"); // 키에 ':' 가 있어도 끝까지 키
        verify(h).evictLocal("drive.example.com:8443");

        listener.handle("self:access-policy:4"); // 자기 메시지
        verify(a, never()).evictLocal("4");

        assertThatNoException().isThrownBy(() -> listener.handle("other-was:unknown:1"));
        assertThatNoException().isThrownBy(() -> listener.handle("broken"));
        willThrow(new NumberFormatException("x")).given(a).evictLocal("x");
        assertThatNoException().isThrownBy(() -> listener.handle("other-was:access-policy:x"));
    }
}
