package com.thinkfree.drive.oauth2.service;

import com.thinkfree.drive.oauth2.properties.OAuth2RegistrationProperty;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** 익명 DCR 등록 횟수 제한 — IP 별 메모리 카운터와 DB 기준 전역 상한. */
@DisplayName("OAuth2ClientRegistrationRateLimiter — IP 별·전역 등록 제한")
class OAuth2ClientRegistrationRateLimiterTest {

    private final DSLContext dsl = mock(DSLContext.class);

    private OAuth2ClientRegistrationRateLimiter limiter(int perIp, int global) {
        return new OAuth2ClientRegistrationRateLimiter(dsl,
                new OAuth2RegistrationProperty(perIp, global, Duration.ofHours(1), Duration.ofDays(7)));
    }

    private void recentRegistrations(int count) {
        given(dsl.fetchCount(any(Table.class), any(Condition.class))).willReturn(count);
    }

    @Test
    @DisplayName("IP 별 상한까지 허용하고 넘으면 거부, 다른 IP 는 따로 센다")
    void perIpLimit() {
        recentRegistrations(0);
        OAuth2ClientRegistrationRateLimiter limiter = limiter(3, 500);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    @DisplayName("전역 상한(window 안 DB 등록 수)에 닿으면 새 IP 도 거부, IP 카운트는 올리지 않는다")
    void globalLimit() {
        OAuth2ClientRegistrationRateLimiter limiter = limiter(1, 500);
        recentRegistrations(500);

        assertThat(limiter.tryAcquire("10.0.0.9")).isFalse();

        recentRegistrations(0);
        assertThat(limiter.tryAcquire("10.0.0.9")).isTrue(); // 거부된 시도는 IP 카운트에 남지 않았다
    }

    @Test
    @DisplayName("IP 를 알 수 없으면 한 버킷(unknown)으로 묶어 센다")
    void unknownIp() {
        recentRegistrations(0);
        OAuth2ClientRegistrationRateLimiter limiter = limiter(1, 500);

        assertThat(limiter.tryAcquire(null)).isTrue();
        assertThat(limiter.tryAcquire(null)).isFalse();
    }
}
