package com.thinkfree.drive.auth.token.blacklist.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Redis 를 쓰지 않는 설치 — 사본 없음, 매 요청 DB 로 확인 */
@Component
@ConditionalOnProperty(name = "drive.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoopBlacklistStore implements BlacklistStore {

    @Override
    public Boolean contains(String tokenHash) {
        return null;
    }

    @Override
    public void add(String tokenHash, Instant expiresAt) {
        // 사본 없음
    }
}
