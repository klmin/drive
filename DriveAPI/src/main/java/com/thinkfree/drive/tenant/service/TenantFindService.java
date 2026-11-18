package com.thinkfree.drive.tenant.service;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.query.TenantListQuery;

import java.util.List;

public interface TenantFindService {

    TenantData findByTenantSeq(int tenantSeq);
    TenantData findByDomain(String domain);
    /** PATH 테넌트 모드 — 경로 슬러그 = SUBDOMAIN 직접 조회(베이스 도메인 무관). */
    TenantData findBySubdomain(String subdomain);
    /** 전체 SELF_MANAGED 테넌트 seq (도메인/상태 무관) — 공유설정 reconcile cleanup 대상. SYSTEM_MANAGED 는 전역 정책 강제를 받지 않아 제외 */
    List<Integer> findSelfManagedTenantSeqs();
    Integer findSingleActiveSelfManagedTenantSeq();
    int countActiveSelfManagedTenants();
    List<TenantData> findAll(TenantListQuery query);
    /** 같은 domain 이 있는지 — excludeTenantSeq 가 있으면 자기 자신 제외(수정 시). */
    boolean existsByDomain(String domain, Integer excludeTenantSeq);
}
