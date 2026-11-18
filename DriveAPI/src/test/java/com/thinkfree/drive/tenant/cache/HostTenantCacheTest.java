package com.thinkfree.drive.tenant.cache;

import com.thinkfree.drive.tenant.properties.TenantProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HostTenantCache 단위 테스트")
class HostTenantCacheTest {

    private static final String HOST_A = "a.drive.thinkfree.com";
    private static final String HOST_B = "b.drive.thinkfree.com";

    private HostTenantCache cache(long ttlSeconds, int maxSize) {
        TenantProperty property = new TenantProperty(ttlSeconds, maxSize, false, null);
        return new HostTenantCache(property);
    }

    @Nested
    @DisplayName("기본 read/write")
    class BasicOps {

        @Test
        @DisplayName("get 은 put 한 값을 그대로 반환한다")
        void get_returns_put_value() {
            HostTenantCache cache = cache(300L, 100);

            cache.put(HOST_A, 42);

            assertThat(cache.get(HOST_A)).isEqualTo(42);
        }

        @Test
        @DisplayName("저장되지 않은 host 는 null 을 반환한다")
        void get_returns_null_for_unknown_host() {
            HostTenantCache cache = cache(300L, 100);

            assertThat(cache.get("missing.example.com")).isNull();
        }

        @Test
        @DisplayName("같은 host 에 다시 put 하면 값이 갱신된다")
        void put_overwrites_existing_value() {
            HostTenantCache cache = cache(300L, 100);
            cache.put(HOST_A, 1);

            cache.put(HOST_A, 99);

            assertThat(cache.get(HOST_A)).isEqualTo(99);
        }

        @Test
        @DisplayName("서로 다른 host 는 독립적으로 저장된다")
        void hosts_are_independent() {
            HostTenantCache cache = cache(300L, 100);

            cache.put(HOST_A, 1);
            cache.put(HOST_B, 2);

            assertThat(cache.get(HOST_A)).isEqualTo(1);
            assertThat(cache.get(HOST_B)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("invalidate")
    class Invalidate {

        @Test
        @DisplayName("invalidate(host) 는 해당 host 만 제거한다")
        void invalidate_removes_only_target_host() {
            HostTenantCache cache = cache(300L, 100);
            cache.put(HOST_A, 1);
            cache.put(HOST_B, 2);

            cache.invalidate(HOST_A);

            assertThat(cache.get(HOST_A)).isNull();
            assertThat(cache.get(HOST_B)).isEqualTo(2);
        }

        @Test
        @DisplayName("invalidateAll 은 모든 entry 를 제거한다")
        void invalidateAll_clears_everything() {
            HostTenantCache cache = cache(300L, 100);
            cache.put(HOST_A, 1);
            cache.put(HOST_B, 2);

            cache.invalidateAll();

            assertThat(cache.get(HOST_A)).isNull();
            assertThat(cache.get(HOST_B)).isNull();
        }

        @Test
        @DisplayName("트랜잭션 안 invalidateAll — 커밋 전에 다시 캐시된 이전 매핑도 커밋 뒤 한 번 더 지운다(작업 E14)")
        void invalidateAll_inTransaction_clearsAgainAfterCompletion() {
            HostTenantCache cache = cache(300L, 100);
            cache.put(HOST_A, 1);
            TransactionSynchronizationManager.initSynchronization();
            try {
                cache.invalidateAll();
                assertThat(cache.get(HOST_A)).as("즉시 무효화는 그대로").isNull();

                cache.put(HOST_A, 1); // 커밋 전 동시 요청이 아직 커밋 안 된 DB 를 읽고 이전 매핑을 다시 캐시

                for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                    s.afterCommit();
                    s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                }
                assertThat(cache.get(HOST_A)).isNull();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("존재하지 않는 host invalidate 는 예외 없이 통과한다")
        void invalidate_unknown_host_is_noop() {
            HostTenantCache cache = cache(300L, 100);

            cache.invalidate("missing.example.com");

            assertThat(cache.get("missing.example.com")).isNull();
        }
    }

    @Nested
    @DisplayName("TTL 만료")
    class Expiration {

        @Test
        @DisplayName("expireAfterWrite TTL 경과 후 entry 는 null 을 반환한다")
        void entry_expires_after_ttl() throws InterruptedException {
            // 1초 TTL — 짧게 잡고 1.2초 대기
            HostTenantCache cache = cache(1L, 100);
            cache.put(HOST_A, 1);
            assertThat(cache.get(HOST_A)).isEqualTo(1);

            Thread.sleep(1200L);

            assertThat(cache.get(HOST_A)).isNull();
        }
    }

    @Nested
    @DisplayName("maximumSize eviction")
    class SizeEviction {

        @Test
        @DisplayName("maximumSize 를 초과해 put 해도 예외 없이 동작한다 (LRU eviction)")
        void evicts_when_size_exceeds_max() {
            // max 2 로 작게 잡고 3개 put → 1개는 LRU eviction 으로 빠짐
            HostTenantCache cache = cache(300L, 2);
            cache.put(HOST_A, 1);
            cache.put(HOST_B, 2);
            cache.put("c.drive.thinkfree.com", 3);

            // Caffeine 은 비동기 maintenance 라 즉시 반영 X — 마지막 put 은 항상 살아있음
            assertThat(cache.get("c.drive.thinkfree.com")).isEqualTo(3);
            // A/B 중 어느 쪽이 evict 됐는지는 W-TinyLFU 정책에 의존 → 개수만 검증하지 않고
            // 마지막 entry 가 정상 저장됐는지만 확인한다.
        }
    }
}
