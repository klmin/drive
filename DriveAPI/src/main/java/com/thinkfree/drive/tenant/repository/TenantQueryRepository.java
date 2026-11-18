package com.thinkfree.drive.tenant.repository;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.query.TenantListQuery;

import java.util.List;

/** 읽기 전용(FindService 가 주입). {@link TenantData}·스칼라 조회 — JPQL 생성자 식(엔티티 반환 없음). 쓰기는 {@link TenantRepository}. */
public interface TenantQueryRepository {

    TenantData findByTenantSeq(Integer tenantSeq);
    TenantData findByDomain(String domain);
    /** PATH 테넌트 모드 — 경로 슬러그 = SUBDOMAIN 직접 조회(베이스 도메인 무관). 중복 시 TENANT_SEQ 최솟값. */
    TenantData findBySubdomain(String subdomain);
    List<Integer> findSelfManagedTenantSeqs();
    List<Integer> findActiveSelfManagedTenantSeqs();
    int countActiveSelfManagedTenants();
    List<TenantData> findAll(TenantListQuery query);
    boolean existsByDomain(String domain, Integer excludeTenantSeq);
}
