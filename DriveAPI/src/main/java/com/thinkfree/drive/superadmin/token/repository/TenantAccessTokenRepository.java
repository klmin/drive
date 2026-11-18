package com.thinkfree.drive.superadmin.token.repository;

import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;

import java.time.Instant;

public interface TenantAccessTokenRepository {
    void save(TenantAccessToken entity);
    void deleteByTenantSeq(Integer tenantSeq);
    TenantAccessToken findByTokenHash(String tokenHash);
    /** 삭제한 행 수 — 0 이면 이미 소비된 토큰(동시 소비 포함), 호출부가 거부한다 */
    int deleteByTokenHash(String tokenHash);
    int deleteExpiredTokens(Instant expirationThreshold);
}
