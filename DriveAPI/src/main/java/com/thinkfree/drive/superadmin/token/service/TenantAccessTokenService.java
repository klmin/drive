package com.thinkfree.drive.superadmin.token.service;

import com.thinkfree.drive.superadmin.token.result.TenantAccessTokenConsumeResult;

public interface TenantAccessTokenService {
    String issue(Integer superAdminUserSeq, Integer tenantSeq);
    TenantAccessTokenConsumeResult consumeAndGet(String token);
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    void deleteByTenant(Integer tenantSeq);
}
