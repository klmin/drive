package com.thinkfree.drive.oauth2.service;

import com.thinkfree.drive.global.ratelimit.RateLimitRule;
import com.thinkfree.drive.global.ratelimit.RateLimiter;
import com.thinkfree.drive.oauth2.properties.OAuth2RegistrationProperty;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

import static com.thinkfree.drive.jooq.Tables.OAUTH2_REGISTERED_CLIENT;

/**
 * 익명 DCR 등록 횟수 제한 — 공개 엔드포인트라 무제한이면 oauth2_registered_client 를 반복 등록으로 채울 수 있다.
 *
 * <ul>
 *   <li>IP 별: 공용 {@link RateLimiter} 토큰 버킷(용량 {@code perIpLimit}, {@code window} 동안 다시 채움).
 *       Redis 를 켜면 WAS 끼리 합산, 끄면 WAS 별(실제 한도 = 설정 × 대수)</li>
 *   <li>전역: window 안에 발급된 클라이언트 수를 DB 로 센다 — 인스턴스·IP 와 무관한 최종 상한(동시 요청 수만큼 넘을 수 있는 근사치)</li>
 * </ul>
 * IP 는 {@code request.getRemoteAddr()} 를 받는다 — RemoteIpValve 가 신뢰 프록시의 X-Forwarded-For 만 반영하므로 위조로 우회할 수 없다.
 * 클라이언트 등록은 테넌트에 속하지 않는 전역 자원이라(oauth2_registered_client 에 테넌트 컬럼 없음) 키에 테넌트를 넣지 않는다.
 * IP 버킷이 먼저 — DB 를 세기 전에 막는다. 전역 상한에 걸린 시도도 IP 토큰을 쓴다(그때는 모두 거절되므로 무해).
 */
@Slf4j
@Component
public class OAuth2ClientRegistrationRateLimiter {

    static final String RULE_NAME = "dcr-ip";

    private final DSLContext dsl;
    private final OAuth2RegistrationProperty property;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final RateLimitRule perIp;

    public OAuth2ClientRegistrationRateLimiter(DSLContext dsl, OAuth2RegistrationProperty property,
                                               RateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.dsl = dsl;
        this.property = property;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.perIp = new RateLimitRule(RULE_NAME, property.perIpLimit(), property.window());
    }

    /**
     * 등록 허용 여부.
     * @return 허용이면 {@link Duration#ZERO}, 거절이면 다시 시도할 수 있을 때까지(양수)
     */
    public Duration tryAcquire(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        Duration wait = rateLimiter.tryConsume(key, perIp);
        if (!wait.isZero()) {
            meterRegistry.counter("drive.rate.limit.rejected", "rule", RULE_NAME).increment();
            log.warn("[OAuth2 DCR] per-IP registration limit exceeded. ip : {} limit : {}", key, property.perIpLimit());
            return wait;
        }
        int recent = dsl.fetchCount(OAUTH2_REGISTERED_CLIENT,
                OAUTH2_REGISTERED_CLIENT.CLIENT_ID_ISSUED_AT.gt(Instant.now().minus(property.window())));
        if (recent >= property.globalLimit()) {
            meterRegistry.counter("drive.rate.limit.rejected", "rule", "dcr-global").increment();
            log.warn("[OAuth2 DCR] global registration limit exceeded. recent : {} limit : {}", recent, property.globalLimit());
            return property.window();
        }
        return Duration.ZERO;
    }
}
