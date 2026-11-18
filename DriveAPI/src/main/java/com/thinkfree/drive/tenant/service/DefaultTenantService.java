package com.thinkfree.drive.tenant.service;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.common.validation.DomainComposer;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.converter.TenantConverter;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.dto.TenantUpdateDto;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.repository.TenantRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 테넌트 쓰기 — 고칠 행은 {@link TenantRepository} 에서 관리 엔티티로 한 번 로드해 {@link Tenant} 변경 메서드로 고친다(커밋 때 변경 감지, 수정 감사는 Auditing — W6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultTenantService implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantConverter tenantConverter;
    private final TenantFindService tenantFindService;
    private final HostTenantCache hostTenantCache;
    /** 합성 도메인의 허용 형식은 주소 모드가 정한다 — SUBDOMAIN 은 도메인만, PATH 는 IPv4 베이스 위 합성값도. */
    private final TenantAddressingStrategy addressingStrategy;

    @Override
    public int save(TenantSaveDto dto) {
        var entity = tenantConverter.toSaveTenant(dto);
        validateDomain(entity.getDomain(), null);
        tenantRepository.insert(entity);
        hostTenantCache.invalidateAll();
        return entity.getTenantSeq();
    }

    /** 없는 테넌트면 TENANT_NOT_FOUND(W6 — 이전엔 아무것도 안 하고 통과). 형식·중복 검사가 먼저. */
    @Override
    public void update(TenantUpdateDto dto) {
        validateDomain(dto.getDomain(), dto.getTenantSeq());
        Tenant tenant = requireTenant(dto.getTenantSeq());
        tenant.changeProfile(dto.getTenantName(), dto.getDomain(), dto.getSubdomain(), dto.getBaseDomainSeq());
        hostTenantCache.invalidateAll();
    }

    @Override
    public void lock(int tenantSeq) {
        DriveAssert.throwIfNull(tenantRepository.lockById(tenantSeq), TenantErrorCode.TENANT_NOT_FOUND);
    }

    /**
     * 일반 설정의 클라이언트 도메인 갱신(단일 테넌트) — 한 번 로드해 "이전 domain 이 비었는지" 판정과 변경을 함께 한다(W6, UseCase 의 판정용 조회 통합).
     * domain 은 호출부가 정규화한 값이라 여기서는 형식만 본다. 없는 테넌트면 TENANT_NOT_FOUND. 캐시 무효화는 호출부.
     */
    @Override
    public boolean rebindDomain(int tenantSeq, String subdomain, Integer baseDomainSeq, String domain) {
        requireAcceptableDomain(domain);
        Tenant tenant = requireTenant(tenantSeq);
        boolean created = !StringUtils.hasText(tenant.getDomain());
        tenant.changeDomainBinding(domain, subdomain, baseDomainSeq);
        return created;
    }

    /**
     * 베이스 도메인 등록·변경에 따른 도메인 바인딩 일괄 재계산 — 대상 행을 관리 엔티티 목록으로 한 번 로드해 고친다(W6, UseCase 의 목록 조회 + 행마다 재로드 통합).
     * firstBinding(이전 베이스 도메인 없음) = 도메인이 있는 SELF_MANAGED 테넌트 전부(기존 domain 의 첫 라벨을 서브도메인으로), 아니면 이 베이스 도메인에 묶인 테넌트(기존 서브도메인 유지).
     * 캐시 무효화는 호출부.
     */
    @Override
    public void rebindToBaseDomain(int baseDomainSeq, String baseDomain, boolean firstBinding) {
        List<Tenant> tenants = firstBinding
                ? tenantRepository.findByTenantTypeAndDomainIsNotNull(TenantType.SELF_MANAGED)
                : tenantRepository.findByBaseDomainSeq(baseDomainSeq);
        for (Tenant tenant : tenants) {
            String subdomain = firstBinding ? DomainComposer.extractLeadingSubdomain(tenant.getDomain()) : tenant.getSubdomain();
            String domain = DomainComposer.compose(subdomain, baseDomain);
            requireAcceptableDomain(domain);
            tenant.changeDomainBinding(domain, subdomain, baseDomainSeq);
        }
    }

    /** 없는 테넌트면 TENANT_LOGIN_TYPE_UPDATE_FAIL(기존 0행 실패와 같은 오류), 같은 값이면 UPDATE 없음. */
    @Override
    public void updateLoginType(int tenantSeq, LoginType loginType) {
        Tenant tenant = tenantRepository.findById(tenantSeq);
        DriveAssert.throwIfNull(tenant, TenantErrorCode.TENANT_LOGIN_TYPE_UPDATE_FAIL);
        tenant.changeLoginType(loginType);
    }

    private Tenant requireTenant(int tenantSeq) {
        Tenant tenant = tenantRepository.findById(tenantSeq);
        DriveAssert.throwIfNull(tenant, TenantErrorCode.TENANT_NOT_FOUND);
        return tenant;
    }

    private void validateDomain(String domain, Integer tenantSeq) {
        if (!StringUtils.hasText(domain)) {
            return;
        }

        requireAcceptableDomain(domain);

        if (tenantFindService.existsByDomain(domain, tenantSeq)) {
            throw ApiRuntimeException.type(TenantErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
        }
    }

    private void requireAcceptableDomain(String domain) {
        if (!addressingStrategy.isAcceptableTenantDomain(domain)) {
            throw ApiRuntimeException.type(TenantErrorCode.INVALID_TENANT_DOMAIN);
        }
    }


    @Override
    public void delete(int tenantSeq) {
        DriveAssert.throwIfZero(tenantRepository.deleteById(tenantSeq), TenantErrorCode.TENANT_NOT_FOUND);
        // 호스트 캐시는 커밋 후에 비운다 — 커밋 전에 비우면 그사이 같은 호스트 요청이 아직 보이는 행으로 다시 채워
        // 삭제된 테넌트가 TTL 동안 남는다. 롤백되면 테넌트가 그대로이므로 비울 필요가 없다(LicenseService.deleteByTenant 와 같은 방식)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                hostTenantCache.invalidateAll();
            }
        });
    }
}
