package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_sso_provider 읽기 — 전부 JPQL({@link SsoProviderJpaRepository}): 조인 DTO·BASIC DTO 는 생성자 식, 존재 검사 3종은 count. 엔티티를 반환·로드하지 않아 detach 불필요(W4).
 * 쓰기는 {@link DefaultSsoProviderRepository}.
 * <p>
 * 조회 결과는 두 형태:
 * <ul>
 *   <li>FULL — 등록/수정자 조인(tb_user c·d) 포함 17컬럼: findAll(테넌트 INNER JOIN)·findAllGlobal(테넌트 없음 → tenant_name NULL)·findById(테넌트 LEFT JOIN)</li>
 *   <li>BASIC — 감사 없는 9컬럼: findActiveByTenantAndProtocol(테넌트 INNER JOIN)·findActiveByGlobalAndProtocol(tenant_name NULL, Limit 1)</li>
 * </ul>
 * 선택 조건(tenantSeq·protocolType·cursorId)은 JPQL `(:x is null or …)`. 커서는 page 가 있고 cursorId 가 있을 때만, FETCH FIRST 는 page 가 있을 때만(XML 동일).
 */
@Repository
@RequiredArgsConstructor
public class DefaultSsoProviderQueryRepository implements SsoProviderQueryRepository {

    private final SsoProviderJpaRepository jpaRepository;

    /** 커서는 page 가 있고 cursorId 가 있을 때만, 건수 제한은 page 가 있을 때만(구 XML 동일). */
    @Override
    public List<SsoProviderData> findAll(SsoProviderListQuery q) {
        CursorPaginationQuery page = q.getPage();
        Integer cursorId = page == null || page.getCursorId() == null ? null : Math.toIntExact(page.getCursorId());
        Limit limit = page == null || page.getPageSize() == null ? Limit.unlimited() : Limit.of(page.getPageSize());
        return jpaRepository.findFullByTenantScope(SsoProviderScope.TENANT, q.getTenantSeq(), q.getProtocolType(), cursorId, limit);
    }

    @Override
    public List<SsoProviderData> findAllGlobal(SsoProviderGlobalListQuery q) {
        return jpaRepository.findFullByGlobalScope(SsoProviderScope.GLOBAL, q.getProtocolType());
    }

    @Override
    public SsoProviderData findById(SsoProviderFindQuery q) {
        return q.getSsoProviderSeq() == null ? null : jpaRepository.findFullById(q.getSsoProviderSeq()).orElse(null);
    }

    @Override
    public SsoProviderData findActiveByTenantAndProtocol(SsoProviderByProtocolQuery q) {
        return jpaRepository.findBasicByTenantAndProtocol(SsoProviderScope.TENANT, q.getTenantSeq(), q.getProtocolType(), q.getStatus()).orElse(null);
    }

    @Override
    public SsoProviderData findActiveByGlobalAndProtocol(SsoProviderGlobalByProtocolQuery q) {
        return jpaRepository.findBasicByScopeAndProtocol(SsoProviderScope.GLOBAL, q.getProtocolType(), q.getStatus(), Limit.of(1))
                .stream().findFirst()
                .orElse(null);
    }

    @Override
    public boolean existsActiveSingleType(SsoProviderActiveCheckQuery q) {
        return jpaRepository.existsByScopeAndTenant(SsoProviderScope.TENANT, q.getTenantSeq(), SsoProviderStatus.ACTIVE, q.getProtocolType());
    }

    @Override
    public boolean existsActiveSingleTypeByGlobal(SsoProviderGlobalActiveCheckQuery q) {
        return jpaRepository.existsByScope(SsoProviderScope.GLOBAL, SsoProviderStatus.ACTIVE, q.getProtocolType());
    }

    /** tenantSeq 가 null 이면 {@code tenant_seq IS NULL}(GLOBAL), 있으면 {@code = :tenantSeq} — XML {@code <choose>} 와 동일(isNotEmpty 가 아닌 null 판정). */
    @Override
    public boolean existsDisplayName(SsoProviderDisplayNameCheckQuery q) {
        return q.getTenantSeq() != null
                ? jpaRepository.existsDisplayNameInTenant(q.getScope(), q.getTenantSeq(), q.getDisplayName(), q.getExcludeSsoProviderSeq())
                : jpaRepository.existsDisplayNameWithoutTenant(q.getScope(), q.getDisplayName(), q.getExcludeSsoProviderSeq());
    }
}
