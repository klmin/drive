package com.thinkfree.drive.superadmin.token.result;

/** 테넌트 진입 키 소비 결과 — 키를 발급한 슈퍼관리자와 대상 테넌트(행은 소비 시 삭제됨) */
public record TenantAccessTokenConsumeResult
(
    Integer superAdminUserSeq,
    Integer tenantSeq
)
{
}
