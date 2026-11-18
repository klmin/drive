package com.thinkfree.drive.tenant.repository;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.query.TenantListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_tenant 읽기 — 전부 JPQL({@link TenantJpaRepository}) DTO·스칼라, 엔티티를 영속성 컨텍스트에 올리지 않는다(생성자 식 — detach 불필요, W6). 쓰기는 {@link DefaultTenantRepository}.
 * <p>
 * 조회 결과는 두 형태:
 * <ul>
 *   <li>FULL — 등록/수정자 조인(tb_user b·c) 포함 16컬럼: findByTenantSeq·findAll(목록 화면)</li>
 *   <li>BASIC — 조인 없는 8컬럼(감사 필드 null): findByDomain·findBySubdomain(요청마다 타는 판별·캐시 경로)</li>
 * </ul>
 * XML 과 다른 점 = findByTenantSeq 에 tenant_type, findAll 에 login_type 을 추가로 내려 FULL 을 한 형태로 통일(상위집합, 소비자 영향 없음).
 */
@Repository
@RequiredArgsConstructor
public class DefaultTenantQueryRepository implements TenantQueryRepository {

    private final TenantJpaRepository jpaRepository;

    @Override
    public TenantData findByTenantSeq(Integer tenantSeq) {
        return tenantSeq == null ? null : jpaRepository.findFullByTenantSeq(tenantSeq).orElse(null);
    }

    @Override
    public TenantData findByDomain(String domain) {
        return jpaRepository.findBasicByDomain(domain).orElse(null);
    }

    @Override
    public TenantData findBySubdomain(String subdomain) {
        return jpaRepository.findBasicBySubdomain(subdomain, Limit.of(1)).stream().findFirst().orElse(null);
    }

    @Override
    public List<Integer> findSelfManagedTenantSeqs() {
        return jpaRepository.findSeqsByType(TenantType.SELF_MANAGED);
    }

    @Override
    public List<Integer> findActiveSelfManagedTenantSeqs() {
        return jpaRepository.findSeqsByTypeAndStatus(TenantType.SELF_MANAGED, TenantStatus.ACTIVE, Limit.of(2));
    }

    @Override
    public int countActiveSelfManagedTenants() {
        return Math.toIntExact(jpaRepository.countByTenantTypeAndStatus(TenantType.SELF_MANAGED, TenantStatus.ACTIVE));
    }

    /** 커서 = tenant_seq DESC 단일 키(cursorId 미만), 없으면 첫 페이지. 유형 필터는 있을 때만. */
    @Override
    public List<TenantData> findAll(TenantListQuery query) {
        var page = query.getPage();
        Limit limit = page.getPageSize() == null ? Limit.unlimited() : Limit.of(page.getPageSize()); // null = 제한 없음(구 FETCH FIRST NULL)
        TenantType tenantType = query.getTenantType();
        if (tenantType == null) {
            return page.getCursorId() == null
                    ? jpaRepository.findFullPage(limit)
                    : jpaRepository.findFullPageAfter(Math.toIntExact(page.getCursorId()), limit);
        }
        return page.getCursorId() == null
                ? jpaRepository.findFullPageByType(tenantType, limit)
                : jpaRepository.findFullPageByTypeAfter(tenantType, Math.toIntExact(page.getCursorId()), limit);
    }

    /** 같은 domain 이 있는지 — excludeTenantSeq 가 있으면 자기 자신 제외(수정 시). */
    @Override
    public boolean existsByDomain(String domain, Integer excludeTenantSeq) {
        return excludeTenantSeq == null
                ? jpaRepository.existsDomain(domain)
                : jpaRepository.existsDomainExcept(domain, excludeTenantSeq);
    }
}
