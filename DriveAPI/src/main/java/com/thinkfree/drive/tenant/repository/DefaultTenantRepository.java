package com.thinkfree.drive.tenant.repository;

import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * tb_tenant 쓰기 — 전부 JPA(JdbcClient 0). 읽기는 {@link DefaultTenantQueryRepository}.
 * <ul>
 *   <li>{@code insert} = {@link TenantJpaRepository#save}(IDENTITY 라 호출 순간 INSERT·PK 주입, 감사는 Spring Data Auditing)</li>
 *   <li>조회 3종 = 고칠 행 로드(관리 엔티티) — Service 가 {@link Tenant} 변경 메서드로 고치면 커밋 때 변경 감지(W6, flush·detach 없음)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultTenantRepository implements TenantRepository {

    private final TenantJpaRepository tenantJpaRepository;

    @Override
    @Transactional
    public void insert(Tenant tenant) {
        tenantJpaRepository.save(tenant);
    }

    @Override
    public Tenant findById(int tenantSeq) {
        return tenantJpaRepository.findById(tenantSeq).orElse(null);
    }

    @Override
    public Tenant lockById(int tenantSeq) {
        return tenantJpaRepository.lockByTenantSeq(tenantSeq).orElse(null);
    }

    @Override
    public List<Tenant> findByTenantTypeAndDomainIsNotNull(TenantType tenantType) {
        return tenantJpaRepository.findByTenantTypeAndDomainIsNotNull(tenantType);
    }

    @Override
    public List<Tenant> findByBaseDomainSeq(int baseDomainSeq) {
        return tenantJpaRepository.findByBaseDomainSeq(baseDomainSeq);
    }

    @Override
    public int deleteById(int tenantSeq) {
        return tenantJpaRepository.deleteByTenantSeq(tenantSeq);
    }
}
