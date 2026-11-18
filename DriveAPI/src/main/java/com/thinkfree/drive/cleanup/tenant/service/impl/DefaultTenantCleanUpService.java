package com.thinkfree.drive.cleanup.tenant.service.impl;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.branding.service.BrandingService;
import com.thinkfree.drive.cleanup.tenant.service.TenantCleanUpService;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.smtpconfig.service.SmtpConfigService;
import com.thinkfree.drive.sso.provider.service.SsoProviderService;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테넌트 범위 데이터 정리 — 사용자 영구 삭제({@code DefaultUserCleanUpService})처럼 모듈마다 서비스의 deleteByTenant 를 부른다.
 * 모듈 메모리 상태(라이선스 캐시)는 각 서비스가 커밋 후 비운다. 조건이 전부 tenant_seq = ? 라 전역 행(tenant_seq NULL)은 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultTenantCleanUpService implements TenantCleanUpService {

    private final AccessAllowedIpService accessAllowedIpService;
    private final AccessAllowedCountryService accessAllowedCountryService;
    private final ApiKeyService apiKeyService;
    private final BrandingService brandingService;
    private final LicenseService licenseService;
    private final ShareSettingService shareSettingService;
    private final SmtpConfigService smtpConfigService;
    private final SsoProviderService ssoProviderService;
    private final TenantAccessTokenService tenantAccessTokenService;
    private final SystemConfigService systemConfigService;

    @Override
    public void deleteTenantScopedData(int tenantSeq) {
        accessAllowedIpService.deleteByTenant(tenantSeq);
        accessAllowedCountryService.deleteByTenant(tenantSeq);
        // 사용자 삭제가 소유 키를 지운 뒤 남은 키(테넌트에 속한 다른 소유자 키 등)
        apiKeyService.deleteByTenant(tenantSeq);
        brandingService.deleteByTenant(tenantSeq);
        licenseService.deleteByTenant(tenantSeq);
        shareSettingService.deleteByTenant(tenantSeq);
        smtpConfigService.deleteByTenant(tenantSeq);
        ssoProviderService.deleteByTenant(tenantSeq);
        tenantAccessTokenService.deleteByTenant(tenantSeq);
        systemConfigService.deleteByTenant(tenantSeq);
    }
}
