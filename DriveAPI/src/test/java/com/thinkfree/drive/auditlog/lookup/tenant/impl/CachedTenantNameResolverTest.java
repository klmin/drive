package com.thinkfree.drive.auditlog.lookup.tenant.impl;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("CachedTenantNameResolver — 5분 TTL")
class CachedTenantNameResolverTest {

    final TenantFindService findService = mock(TenantFindService.class);
    final AtomicLong nanos = new AtomicLong();
    final CachedTenantNameResolver resolver = new CachedTenantNameResolver(findService, nanos::get);

    private static TenantData tenant(String name) {
        TenantData data = mock(TenantData.class);
        given(data.getTenantName()).willReturn(name);
        return data;
    }

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    @DisplayName("TTL 안에서는 다시 조회하지 않고, 지나면 바뀐 이름을 읽는다")
    void renameVisibleAfterTtl() {
        TenantData before = tenant("old");
        given(findService.findByTenantSeq(1)).willReturn(before);
        assertThat(resolver.resolve(1)).isEqualTo("old");

        TenantData after = tenant("new");
        given(findService.findByTenantSeq(1)).willReturn(after);
        advance(CachedTenantNameResolver.TTL.minusSeconds(1));
        assertThat(resolver.resolve(1)).as("TTL 안 — 캐시").isEqualTo("old");
        verify(findService, times(1)).findByTenantSeq(1);

        advance(Duration.ofSeconds(2));
        assertThat(resolver.resolve(1)).as("TTL 지남 — 바뀐 이름").isEqualTo("new");
        verify(findService, times(2)).findByTenantSeq(1);
    }

    @Test
    @DisplayName("테넌트마다 따로 캐시한다")
    void perTenant() {
        TenantData a = tenant("a");
        TenantData b = tenant("b");
        given(findService.findByTenantSeq(1)).willReturn(a);
        given(findService.findByTenantSeq(2)).willReturn(b);

        assertThat(resolver.resolve(1)).isEqualTo("a");
        assertThat(resolver.resolve(2)).isEqualTo("b");
        assertThat(resolver.resolve(1)).isEqualTo("a");
        verify(findService, times(1)).findByTenantSeq(1);
    }

    @Test
    @DisplayName("없는 테넌트·조회 실패는 null 이고 캐시하지 않는다, seq null 은 조회 안 함")
    void missingAndFailureNotCached() {
        given(findService.findByTenantSeq(3)).willReturn(null);
        assertThat(resolver.resolve(3)).isNull();
        assertThat(resolver.resolve(3)).isNull();
        verify(findService, times(2)).findByTenantSeq(3);

        given(findService.findByTenantSeq(4)).willThrow(new IllegalStateException("db down"));
        assertThat(resolver.resolve(4)).isNull();
        TenantData recovered = tenant("back");
        org.mockito.Mockito.reset(findService);
        given(findService.findByTenantSeq(4)).willReturn(recovered);
        assertThat(resolver.resolve(4)).as("실패를 캐시하지 않아 바로 다시 조회").isEqualTo("back");

        assertThat(resolver.resolve(null)).isNull();
        verify(findService, never()).findByTenantSeq(0);
    }
}
