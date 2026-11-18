package com.thinkfree.drive.tenant.policy;

/**
 * 보호 계정(테넌트 생성 때 만든 계정) 판정 종류 — {@link TenantPolicy} 의 보호 계정 메서드와 1:1.
 * 어느 테넌트에서 막는지는 {@link DefaultTenantPolicy#restrictedWhen(ProtectedAccountRule)} 가 정한다.
 */
public enum ProtectedAccountRule {
    /** {@link TenantPolicy#requireManageableUser} — 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경(모든 유형) */
    MANAGE,
    /** {@link TenantPolicy#requireUpdatableUser} — 관리자의 사용자 정보 수정(SYSTEM_MANAGED 만) */
    UPDATE,
    /** {@link TenantPolicy#requireSelfPasswordChangeable} — 본인 비밀번호 변경·재설정(SYSTEM_MANAGED 만) */
    SELF_PASSWORD
}
