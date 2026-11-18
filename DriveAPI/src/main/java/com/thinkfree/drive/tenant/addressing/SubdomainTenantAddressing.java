package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver.Lookup;
import com.thinkfree.drive.util.RequestHostUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SUBDOMAIN 모드({@code tenant.resolution-mode: SUBDOMAIN}). 기본은 PATH 이므로 명시해야 로딩된다.
 * 인바운드: Host(또는 X-Forwarded-Host) 전체 문자열 = {@code TB_TENANT.DOMAIN}. 존재+ACTIVE → MATCHED(원본 요청),
 * 존재+비활성 → INACTIVE, 미등록 → NONE.
 * 인바운드 판별 키는 포트 없는 호스트다 — 베이스 도메인에 포트를 입력해도 {@code tenant.domain} 에는 호스트만 저장된다.
 * 아웃바운드: {@code scheme://tenant.domain[:port]}(베이스에 묶인 테넌트는 베이스에 입력한 스킴·포트 우선). 테넌트 domain 이 비어 있으면(신규 설치 단일 테넌트)
 * 현재 요청 host → {@code app.front.base-url} host 로 폴백.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tenant", name = "resolution-mode", havingValue = "SUBDOMAIN")
public class SubdomainTenantAddressing implements TenantAddressingStrategy {

    private final TenantSeqResolver tenantSeqResolver;
    private final AppFrontProperty appFrontProperty;
    private final BaseDomainFindService baseDomainFindService;

    @Override
    public TenantMatch resolve(HttpServletRequest request) {
        String host = RequestHostUtil.hostName(request);
        System.out.println("host : "+host);
        if (host == null) {
            return TenantMatch.none();
        }
        Lookup lookup = tenantSeqResolver.lookupByHost(host);
        System.out.println("lookup : "+lookup);
        return switch (lookup.status()) {
            case ACTIVE -> TenantMatch.matched(lookup.tenantSeq(), request);
            case INACTIVE -> TenantMatch.inactive();
            case NOT_FOUND -> TenantMatch.none();
        };
    }

    /**
     * 테넌트 domain 이 베이스 도메인에 묶여 있으면({@code base_domain_seq}) 베이스에 입력한 스킴·포트를 그대로 쓴다
     * ({@link OriginComposer#composeOnBase}). 그 외(베이스 없이 일반 설정으로 넣은 domain, 요청 host 폴백)는 호출부 스킴·포트.
     */
    @Override
    public String origin(TenantData tenant, String scheme, Integer port) {
        if (StringUtils.hasText(tenant.getDomain())) {
            return OriginComposer.composeOnBase(boundBaseOrNull(tenant), tenant.getDomain(), scheme, port);
        }
        String host = PublicHostFallback.requestHostOrConfigured(appFrontProperty);
        if (host == null) {
            throw ApiRuntimeException.type(TenantErrorCode.TENANT_HOST_NOT_FOUND);
        }
        return OriginComposer.compose(scheme, host, port);
    }

    private BaseDomainData boundBaseOrNull(TenantData tenant) {
        if (tenant.getBaseDomainSeq() == null) {
            return null;
        }
        BaseDomainData base = baseDomainFindService.findCurrent();
        return (base != null && tenant.getBaseDomainSeq().equals(base.getBaseDomainSeq())) ? base : null;
    }
}
