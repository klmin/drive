package com.thinkfree.drive.global.service;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.thinkfree.drive.constants.DriveConstants.*;

/**
 * 로컬용 ClientDomainService ({@code drive.client-domain.mode=local}, DriveAPI/.env).
 * 현재 요청의 scheme 을 기반으로 테넌트별 도메인을 동적으로 구성한다.
 *
 * 동작 규칙
 * 1) HTTPS 진입 (Caddy 등 reverse proxy로 TLS 종단, FE/BE 같은 origin)
 *    → FE URL / BE URL 모두 요청 scheme + 테넌트 host 그대로 사용 (포트 재할당 X)
 *
 * 2) HTTP 진입 (Caddy 우회, 직접 localhost:8080 으로 들어온 개발 케이스)
 *    → BE URL: 요청 scheme + 테넌트 host + 요청 포트
 *    → FE URL: 포트만 app.front.port 로 교체 (기본 3000)
 *
 * 운영 환경에서는 Caddy/Nginx 경유 HTTPS로만 진입하므로 1)만 타게 된다.
 * X-Forwarded-Proto 반영은 `server.forward-headers-strategy: native` (Tomcat RemoteIpValve) 가 담당한다.
 * host / 테넌트 경로 조립은 {@link TenantAddressingStrategy} (SUBDOMAIN·PATH 모드 분기).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "drive.client-domain.mode", havingValue = "local")
public class LocalClientDomainService implements ClientDomainService {

    private final TenantFindService tenantFindService;
    private final AppFrontProperty appFrontProperty;
    private final TenantAddressingStrategy addressingStrategy;

    @Override
    public String getDomain(int tenantSeq) {
        return build(tenantSeq, appFrontProperty.port());
    }

    @Override
    public String getDomainBackEnd(int tenantSeq) {
        return build(tenantSeq, null);
    }

    private String build(int tenantSeq, Integer targetPort) {
        TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
        HttpServletRequest req = currentRequestOrNull();

        if (req == null) {
            return addressingStrategy.origin(tenant, HTTPS_SCHEME, targetPort);
        }

        String scheme = req.getScheme();
        int port = req.getServerPort();
        if (isStandardPort(scheme, port)) {
            return addressingStrategy.origin(tenant, scheme, null);
        }
        int finalPort = (targetPort != null) ? targetPort : port;
        return addressingStrategy.origin(tenant, scheme, finalPort);
    }

    private static HttpServletRequest currentRequestOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes s) ? s.getRequest() : null;
    }

    private static boolean isStandardPort(String scheme, int port) {
        return (HTTP_SCHEME.equalsIgnoreCase(scheme) && port == 80)
            || (HTTPS_SCHEME.equalsIgnoreCase(scheme) && port == 443);
    }
}
