package com.thinkfree.drive.oauth2.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thinkfree.drive.oauth2.properties.OAuth2RegistrationProperty;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thinkfree.drive.jooq.Tables.OAUTH2_REGISTERED_CLIENT;

/**
 * 익명 DCR 등록 횟수 제한 — 공개 엔드포인트라 무제한이면 oauth2_registered_client 를 반복 등록으로 채울 수 있다.
 *
 * <ul>
 *   <li>IP 별: window 고정 구간 메모리 카운터(Caffeine, 첫 등록 시점부터 window 후 만료). 인스턴스별이라 정확한 전역값은 아니다</li>
 *   <li>전역: window 안에 발급된 클라이언트 수를 DB 로 센다 — 인스턴스·IP 와 무관한 최종 상한</li>
 * </ul>
 * IP 는 {@code request.getRemoteAddr()} 를 받는다 — server.forward-headers-strategy=native(RemoteIpValve)가
 * 신뢰 프록시의 X-Forwarded-For 만 반영하므로, 헤더를 그대로 믿는 IpUtil 과 달리 위조로 우회할 수 없다.
 * Redis 를 쓰지 않는 이유: 이 앱은 Redis 를 런타임에 사용하지 않아 이 기능만을 위해 의존을 늘리지 않는다.
 * 클라이언트 등록은 테넌트에 속하지 않는 전역 자원이라(oauth2_registered_client 에 테넌트 컬럼 없음) 키에 테넌트를 넣지 않는다.
 */
@Slf4j
@Component
public class OAuth2ClientRegistrationRateLimiter {

    private final DSLContext dsl;
    private final OAuth2RegistrationProperty property;
    private final Cache<String, AtomicInteger> perIpCounts;

    public OAuth2ClientRegistrationRateLimiter(DSLContext dsl, OAuth2RegistrationProperty property) {
        this.dsl = dsl;
        this.property = property;
        this.perIpCounts = Caffeine.newBuilder()
                .expireAfterWrite(property.window())
                .maximumSize(100_000)
                .build();
    }

    /** 등록 허용 여부 — 허용이면 IP 카운트를 1 올린다(거부면 올리지 않음). 동시 요청도 먼저 한 칸을 잡고 확인해 상한을 넘지 않는다 */
    public boolean tryAcquire(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        AtomicInteger count = perIpCounts.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() > property.perIpLimit()) {
            count.decrementAndGet();
            log.warn("[OAuth2 DCR] per-IP registration limit exceeded. ip : {} limit : {}", key, property.perIpLimit());
            return false;
        }
        int recent = dsl.fetchCount(OAUTH2_REGISTERED_CLIENT,
                OAUTH2_REGISTERED_CLIENT.CLIENT_ID_ISSUED_AT.gt(Instant.now().minus(property.window())));
        if (recent >= property.globalLimit()) {
            count.decrementAndGet();
            log.warn("[OAuth2 DCR] global registration limit exceeded. recent : {} limit : {}", recent, property.globalLimit());
            return false;
        }
        return true;
    }
}
