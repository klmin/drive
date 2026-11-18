package com.thinkfree.drive.api.v1.superadmin.basedomain.usecase;

import com.thinkfree.drive.api.v1.superadmin.basedomain.request.SuperAdminBaseDomainUpsertRequest;
import com.thinkfree.drive.api.v1.superadmin.basedomain.response.SuperAdminBaseDomainResponse;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.result.BaseDomainUpsertResult;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.basedomain.service.BaseDomainService;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuperAdminBaseDomainUseCase {

    private final BaseDomainFindService baseDomainFindService;
    private final BaseDomainService baseDomainService;
    private final TenantService tenantService;
    private final HostTenantCache hostTenantCache;

    public SuperAdminBaseDomainResponse get() {
        BaseDomainData current = baseDomainFindService.findCurrent();
        if (current == null) {
            return new SuperAdminBaseDomainResponse(null, null, null, null, null);
        }
        return new SuperAdminBaseDomainResponse(current.getBaseDomainSeq(), current.getBaseDomain(),
                current.getScheme(), current.getPort(), current.getAddress());
    }
    
    @Transactional
    public void upsert(SuperAdminBaseDomainUpsertRequest request) {
        
        BaseDomainData current = baseDomainFindService.findCurrent();
        BaseDomainUpsertResult saved = baseDomainService.upsert(request.bd());

        // 첫 등록이면 도메인이 있는 SELF_MANAGED 테넌트 전부, 변경이면 이 베이스 도메인에 묶인 테넌트를 재바인딩(대상 로드·도메인 계산은 Service)
        tenantService.rebindToBaseDomain(saved.baseDomainSeq(), saved.baseDomain(), current == null);

        hostTenantCache.invalidateAll();
    }
}
