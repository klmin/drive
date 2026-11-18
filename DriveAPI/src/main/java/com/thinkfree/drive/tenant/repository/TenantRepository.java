package com.thinkfree.drive.tenant.repository;

import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantType;

import java.util.List;

/** 쓰기(Service 가 주입) — 조회는 전부 고칠 행 로드(관리 엔티티, 커밋 때 변경 감지). 판단·화면 읽기는 {@link TenantQueryRepository}. */
public interface TenantRepository {

    /** JPA save — identity PK 가 엔티티에 채워진다(감사 컬럼은 Spring Data Auditing). */
    void insert(Tenant tenant);
    /** 없으면 null */
    Tenant findById(int tenantSeq);
    /** 행 잠금(FOR UPDATE) 로드. 없으면 null */
    Tenant lockById(int tenantSeq);
    List<Tenant> findByTenantTypeAndDomainIsNotNull(TenantType tenantType);
    List<Tenant> findByBaseDomainSeq(int baseDomainSeq);
    /** 테넌트 행 삭제 — 지운 행 수(없으면 0) */
    int deleteById(int tenantSeq);
}
