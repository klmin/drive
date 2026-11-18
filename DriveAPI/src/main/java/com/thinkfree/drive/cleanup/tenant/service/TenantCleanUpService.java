package com.thinkfree.drive.cleanup.tenant.service;

/** 테넌트 삭제 — 테넌트 범위 데이터 정리. 사용자·사용자 연관 행은 사용자 영구 삭제가 먼저 정리한다 */
public interface TenantCleanUpService {

    /**
     * tenant_seq 가 이 테넌트인 접근 제한·API Key·브랜딩·라이선스·공유 설정·SMTP·SSO·진입 토큰·시스템 설정을 각 모듈 서비스로 지운다.
     * tb_tenant 행·tb_user(DELETED 로 남김)·tb_user_log·감사 로그는 지우지 않는다.
     */
    void deleteTenantScopedData(int tenantSeq);
}
