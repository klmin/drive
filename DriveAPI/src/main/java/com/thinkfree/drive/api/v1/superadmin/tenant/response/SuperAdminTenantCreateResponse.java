package com.thinkfree.drive.api.v1.superadmin.tenant.response;

/**
 * 슈퍼어드민 테넌트 등록 응답 — 이어지는 프로비저닝(API Key 발급·라이선스·office)이 쓰는 키.
 * userSeq 는 함께 만든 관리자 계정(보호 계정).
 */
public record SuperAdminTenantCreateResponse(
        int tenantSeq,
        int userSeq
) {
}
