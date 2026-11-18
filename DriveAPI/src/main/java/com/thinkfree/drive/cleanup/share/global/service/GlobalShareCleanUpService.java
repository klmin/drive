package com.thinkfree.drive.cleanup.share.global.service;

public interface GlobalShareCleanUpService {

    void cleanupAllSharesByTenant(Integer tenantSeq);

    /**
     * reconcile 실행기: plan 에 따라 해당 테넌트의 공유 데이터를 정리한다.
     * fullWipe 는 {@link #cleanupAllSharesByTenant} 로, 선별 삭제(deleteScopes / stripAllTargets /
     * stripOtherTenantTargets / deleteOtherTenantMeta)는 scope별·테넌트외부 선별 쿼리로 처리한다.
     * (PUBLIC scope 는 share_type 3값 마이그레이션 전까지 대상 행 0건)
     */
    void apply(Integer tenantSeq, com.thinkfree.drive.cleanup.share.global.plan.ShareCleanupPlan plan);
}
