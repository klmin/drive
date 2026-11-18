package com.thinkfree.drive.tenant.policy;

/**
 * 테넌트 유형·사용자 모드별 기능 제한 판정. 기능 위반은 SYSTEM_MANAGED_RESTRICTED.
 * 슈퍼관리자 경로는 호출부가 이 판정을 건너뛴다(프로비저닝·복구는 슈퍼관리자가 한다).
 * tenantSeq 가 null(테넌트 없는 슈퍼관리자·익명 계정·키)이면 허용한다.
 */
public interface TenantPolicy {

    /** @return 해당 테넌트에서 기능을 쓸 수 있으면 true, tenantSeq 가 null 이면 true */
    boolean allows(Integer tenantSeq, TenantFeature feature);

    /** {@link #allows} 가 false 면 SYSTEM_MANAGED_RESTRICTED */
    void require(Integer tenantSeq, TenantFeature feature);

    /**
     * 보호 계정(테넌트 생성 때 만든 계정) 관리 — 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경을 막는다(USER_PROTECTED).
     * 보호 API Key(API_KEY_PROTECTED)와 같은 뜻 — 보호면 관리자·사용자 경로에서 바꿀 수 없다.
     */
    void requireManageableUser(int userSeq);

    /**
     * 본인 비밀번호 변경(로그인 본인 변경·재설정 링크·토큰 재설정) — SYSTEM_MANAGED 보호 계정만 막는다(SYSTEM_MANAGED_RESTRICTED, 비밀번호는 외부 시스템 소유).
     * SELF_MANAGED 보호 계정은 슈퍼관리자가 정한 초기 비밀번호를 고객이 바꿔야 하므로 허용.
     */
    void requireSelfPasswordChangeable(int userSeq);

    /** {@link #requireSelfPasswordChangeable} 를 통과하면 true — 거부 대신 분기가 필요한 곳(로그인 응답의 비밀번호 변경 유도 등) */
    boolean isSelfPasswordChangeable(int userSeq);

    /**
     * 관리자의 사용자 정보 수정(이름·용량·연락처·프로필 이미지·역할 — 단건·bulk 수정 항목) — SYSTEM_MANAGED 보호 계정만 막는다(SYSTEM_MANAGED_RESTRICTED).
     * 본인 비밀번호와 같은 이유: 그 계정 값은 외부 시스템(프로비저닝)이 정한다. SELF_MANAGED 보호 계정·보호 아닌 계정(MULTI 보조 계정)은 허용.
     */
    void requireUpdatableUser(int userSeq);
}
