package com.thinkfree.drive.tenant.service;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.query.TenantListQuery;
import com.thinkfree.drive.tenant.repository.TenantQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultTenantFindService implements TenantFindService {

    private final TenantQueryRepository tenantQueryRepository;

    @Override
    public TenantData findByTenantSeq(int tenantSeq) {
        TenantData data = tenantQueryRepository.findByTenantSeq(tenantSeq);
        DriveAssert.throwIfNull(data, TenantErrorCode.TENANT_NOT_FOUND);
        return data;
    }

    @Override
    public TenantData findByDomain(String domain) {
        return tenantQueryRepository.findByDomain(domain);
    }

    @Override
    public TenantData findBySubdomain(String subdomain) {
        return tenantQueryRepository.findBySubdomain(subdomain);
    }

    @Override
    public List<Integer> findSelfManagedTenantSeqs() {
        return tenantQueryRepository.findSelfManagedTenantSeqs();
    }

    /**
     * 활성(ACTIVE) 상태의 SELF_MANAGED 테넌트가 정확히 1개일 때만 그 seq 를 반환(SYSTEM_MANAGED 는 세지 않음).
     * 그 외(0개, 2개 이상)면 null. 단일테넌트 모드 fallback 용도.
     */
    @Override
    public Integer findSingleActiveSelfManagedTenantSeq() {
        List<Integer> tenantSeqList = tenantQueryRepository.findActiveSelfManagedTenantSeqs();
        return tenantSeqList.size() == 1 ? tenantSeqList.get(0) : null;
    }

    @Override
    public int countActiveSelfManagedTenants() {
        return tenantQueryRepository.countActiveSelfManagedTenants();
    }

    @Override
    public List<TenantData> findAll(TenantListQuery query) {
        return tenantQueryRepository.findAll(query);
    }

    @Override
    public boolean existsByDomain(String domain, Integer excludeTenantSeq) {
        return tenantQueryRepository.existsByDomain(domain, excludeTenantSeq);
    }
}
