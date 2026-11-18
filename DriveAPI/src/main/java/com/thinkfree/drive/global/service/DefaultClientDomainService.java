package com.thinkfree.drive.global.service;

import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.thinkfree.drive.constants.DriveConstants.HTTPS_SCHEME;

/**
 * 운영/개발 서버용 ClientDomainService.
 * 요청 scheme 을 그대로 따라간다 (http로 들어오면 http, https면 https). 포트는 붙이지 않는다.
 * 단, 베이스 도메인에 스킴·포트를 입력해 두었으면 그 값이 우선한다({@link TenantAddressingStrategy#origin}).
 *
 * - 요청 컨텍스트가 있으면 X-Forwarded-Proto(=요청 scheme)를 반영한다.
 *   (server.forward-headers-strategy: native 설정으로 reverse proxy 의 X-Forwarded-Proto 가 scheme 에 반영됨)
 * - 요청 컨텍스트가 없으면(스케줄러/비동기 등) https 로 폴백한다.
 * - host / 테넌트 경로 조립은 {@link TenantAddressingStrategy} (SUBDOMAIN·PATH 모드 분기).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "drive.client-domain.mode", havingValue = "default", matchIfMissing = true)
public class DefaultClientDomainService implements ClientDomainService {

    private final TenantFindService tenantFindService;
    private final TenantAddressingStrategy addressingStrategy;

    @Override
    public String getDomain(int tenantSeq) {
        TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
        HttpServletRequest req = currentRequestOrNull();
        String scheme = (req == null) ? HTTPS_SCHEME : req.getScheme();
        return addressingStrategy.origin(tenant, scheme, null);
    }

    @Override
    public String getDomainBackEnd(int tenantSeq) {
        return getDomain(tenantSeq);
    }

    private static HttpServletRequest currentRequestOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }
}
