package com.thinkfree.drive.api.v1.admin.systemconfig.usecase;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.util.MaskingUtils;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.common.validation.DomainComposer;
import com.thinkfree.drive.api.v1.admin.systemconfig.converter.SystemConfigResponseConverter;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.*;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.*;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.systemconfig.exception.SystemConfigErrorCode;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.systemconfig.util.ClientDomainUrls;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminSystemConfigUseCase {

    private final SystemConfigResponseConverter systemConfigResponseConverter;
    private final SystemConfigService systemConfigService;
    private final StorageService storageService;
    private final SystemConfigFindService systemConfigFindService;
    private final AccessAllowedCountryService accessAllowedCountryService;
    private final AccessAllowedIpService accessAllowedIpService;
    private final ShareFindService shareFindService;
    private final TenantFindService tenantFindService;
    private final TenantService tenantService;
    private final BaseDomainFindService baseDomainFindService;
    private final ClientDomainService clientDomainService;
    private final HostTenantCache hostTenantCache;
    private final UserFindService userFindService;
    private final TenantPolicy tenantPolicy;

    public GeneralResponse getGeneralConfig(){
        int tenantSeq = TenantContext.getOrThrow();
        TenantData tenant = tenantFindService.findByTenantSeq(tenantSeq);
        String clientDomain = StringUtils.hasText(tenant.getDomain())
                ? clientDomainService.getDomain(tenantSeq)
                : "";
        if (!tenantPolicy.allows(tenantSeq, TenantFeature.SYSTEM_INFO_VIEW)) {
            clientDomain = MaskingUtils.hideIfPresent(clientDomain);
        }
        return new GeneralResponse(clientDomain);
    }

    /**
     * 일반 설정의 클라이언트 도메인 = 단일 테넌트의 풀 도메인(tb_tenant.domain).
     * 단일 SELF_MANAGED 테넌트일 때만 동작(다중 테넌트는 베이스 도메인/서브도메인 흐름으로 관리).
     * scheme 는 저장하지 않고 ClientDomainService 가 환경별로 결정하므로, 입력값의 스킴은 떼고 호스트만 보관한다.
     */
    @Transactional
    @TenantFeatureRequired(TenantFeature.SYSTEM_CONFIG)
    public void putGeneral(GeneralUpdateRequest request){
        
        if (tenantFindService.findSingleActiveSelfManagedTenantSeq() == null) {
            throw ApiRuntimeException.type(SystemConfigErrorCode.GENERAL_UPDATE_REQUIRES_SUPER_ADMIN);
        }
        
        int tenantSeq = TenantContext.getOrThrow();

        String host = ClientDomainUrls.host(request.clientDomain());
        String subdomain = null;
        Integer baseDomainSeq = null;
        
        BaseDomainData base = baseDomainFindService.findCurrent();
        
        if (base != null) {
            if (!host.endsWith("." + base.getBaseDomain())) {
                throw ApiRuntimeException.type(BaseDomainErrorCode.DOMAIN_BASE_MISMATCH);
            }
            baseDomainSeq = base.getBaseDomainSeq();
            // 슈퍼어드민 테넌트 등록/수정과 같은 형식·예약어 검증 (PATH 모드에서 이 값이 경로 첫 세그먼트가 됨)
            subdomain = DomainComposer.normalizeSubdomain(
                    host.substring(0, host.length() - base.getBaseDomain().length() - 1));
        }

        tenantService.rebindDomain(tenantSeq, subdomain, baseDomainSeq, host);

        hostTenantCache.invalidateAll();
    }

    public OfficeResponse getOfficeConfig(){
        var dto = systemConfigFindService.findOfficeConfig(TenantContext.getOrThrow());
        var response = systemConfigResponseConverter.toOfficeResponse(dto);
        if (systemConfigFindService.isOfficeServiceManaged(TenantContext.getOrThrow())) {
            response = new OfficeResponse(response.officeConnectionType(), response.officeDomain(),
                    response.officeAuthMode(), "", response.officeHttpStorageAdapterName(), "", true);
        }
        if (!tenantPolicy.allows(TenantContext.getOrThrow(), TenantFeature.OFFICE_INFO_VIEW)) {
            // 연동 방식·인증 방식은 선택 항목이라 그대로 둔다
            response = new OfficeResponse(response.officeConnectionType(), MaskingUtils.hideIfPresent(response.officeDomain()),
                    response.officeAuthMode(), MaskingUtils.hideIfPresent(response.officeTicketServiceToken()),
                    MaskingUtils.hideIfPresent(response.officeHttpStorageAdapterName()),
                    MaskingUtils.hideIfPresent(response.officeHttpStorageRequestJwtSecret()), response.serviceManaged());
        }
        return response;
    }

    @TenantFeatureRequired(TenantFeature.SYSTEM_CONFIG)
    public void putOffice(OfficeUpdateRequest request, int requestUserSeq){
        if (systemConfigFindService.isOfficeServiceManaged(TenantContext.getOrThrow())) {
            throw ApiRuntimeException.type(SystemConfigErrorCode.OFFICE_MANAGED_BY_SERVICE);
        }
        systemConfigService.upsertOffice(TenantContext.getOrThrow(), requestUserSeq, request);
    }

    public StorageResponse getStorageInfo(){
        var storageDetailResult = storageService.getStorageInfo();
        if (!tenantPolicy.allows(TenantContext.getOrThrow(), TenantFeature.STORAGE_INFO_VIEW)) {
            storageDetailResult = new StorageDetailResult(storageDetailResult.storageType(),
                    MaskingUtils.hideIfPresent(storageDetailResult.accessKey()), MaskingUtils.hideIfPresent(storageDetailResult.secretKey()),
                    MaskingUtils.hideIfPresent(storageDetailResult.bucketName()), MaskingUtils.hideIfPresent(storageDetailResult.region()),
                    MaskingUtils.hideIfPresent(storageDetailResult.endPoint()), storageDetailResult.status());
        }
        return systemConfigResponseConverter.toStorageResponse(storageDetailResult);
    }

    /** 테넌트 총 용량(키 없으면 null)과 사용자 할당량 합계 — 정책이 할당량 기준이라 사용량은 넣지 않는다. */
    public StorageQuotaResponse getStorageQuota() {
        int tenantSeq = TenantContext.getOrThrow();
        Long totalQuotaByte = systemConfigFindService.findTenantTotalQuotaByte(tenantSeq);
        long allocatedQuotaByte = userFindService.sumTotalQuotaByteByTenantSeq(tenantSeq);
        return new StorageQuotaResponse(totalQuotaByte, allocatedQuotaByte);
    }

    public IntellectResponse getIntellectInfo(){
        var dto = systemConfigFindService.findIntellectConfig(TenantContext.getOrThrow());
        return systemConfigResponseConverter.toIntellectResponse(dto);
    }

    @TenantFeatureRequired(TenantFeature.SYSTEM_CONFIG)
    public void putIntellect(IntellectUpdateRequest request, int requestUserSeq){
        systemConfigService.upsertIntellect(TenantContext.getOrThrow(), requestUserSeq, request);
    }

    public MfaStatusResponse getMfaStatus() {
        var dto = systemConfigFindService.findMfaStatusConfig(TenantContext.getOrThrow());
        return systemConfigResponseConverter.toMfaStatusResponse(dto);
    }

    @TenantFeatureRequired(TenantFeature.MFA_SETTING)
    public void putMfaStatus(MfaStatusUpdateRequest request, int requestUserSeq) {
        systemConfigService.upsertMfaStatus(TenantContext.getOrThrow(), requestUserSeq, request);
    }

    public FileSizeLimitResponse getFileSizeLimit() {
        var dto = systemConfigFindService.findFileSizeLimitConfig(TenantContext.getOrThrow());
        return systemConfigResponseConverter.toFileSizeLimitResponse(dto);
    }

    public void putFileSizeLimit(FileSizeLimitUpdateRequest request, int requestUserSeq) {
        systemConfigService.upsertFileSizeLimit(TenantContext.getOrThrow(), requestUserSeq, request);
    }

    public AccessRestrictionResponse getAccessRestrictionStatus() {
        var tenantSeq = TenantContext.getOrThrow();
        var dto = systemConfigFindService.findAccessRestrictionStatusConfig(tenantSeq);
        var allowedCountries = accessAllowedCountryService.findAllCountryCodes(tenantSeq);
        var allowedIps = accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.USER);
        return systemConfigResponseConverter.toAccessRestrictionStatusResponse(dto, allowedCountries, allowedIps);
    }

    @TenantFeatureRequired(TenantFeature.ACCESS_RESTRICTION)
    public void putAccessRestrictionStatus(AccessRestrictionUpdateRequest request, int requestUserSeq) {
        var tenantSeq = TenantContext.getOrThrow();
        if (Boolean.TRUE.equals(request.enabled())) {
            if (accessAllowedCountryService.findAllCountryCodes(tenantSeq).isEmpty()
                    && accessAllowedIpService.findAllIpCidrs(tenantSeq, RoleType.USER).isEmpty()) {
                throw ApiRuntimeException.type(AccessErrorCode.EMPTY_ALLOWED_COUNTRIES);
            }
        }
        systemConfigService.upsertAccessRestrictionStatus(tenantSeq, requestUserSeq, request);
    }
    

    @TenantFeatureRequired(TenantFeature.SHARE)
    public ShareExistsResponse existsShare() {
        var exists = shareFindService.existsByTenant(TenantContext.getOrThrow());
        return new ShareExistsResponse(exists);
    }

}
