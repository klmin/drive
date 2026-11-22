package com.thinkfree.drive.api.v1.config.usecase;

import com.thinkfree.drive.api.v1.config.converter.ConfigResponseConverter;
import com.thinkfree.drive.api.v1.config.response.ClientConfigResponse;
import com.thinkfree.drive.api.v1.config.response.FileSizeLimitResponse;
import com.thinkfree.drive.api.v1.config.response.OfficeResponse;
import com.thinkfree.drive.api.v1.config.response.TenantConfigResponse;
import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.properties.LicenseEnforcementProperty;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigUseCase {

    private final SystemConfigFindService systemConfigFindService;
    private final TenantFindService tenantFindService;
    private final ConfigResponseConverter configResponseConverter;
    private final ShareSettingFacade shareSettingFacade;
    private final LicenseService licenseService;
    private final SeatLicenseService seatLicenseService;
    private final LicenseEnforcementProperty licenseEnforcementProperty;

    /** 라이선스 검사를 끈 개발 환경의 상태 응답 — 프론트 차단 판정(state)만 통과시킨다. 화이트라벨은 끈 채로 둔다 */
    static final Map<String, Object> LICENSE_ENFORCEMENT_DISABLED_STATUS =
            Map.of("state", true, "paidWhiteLabel", false, "enforcementDisabled", true);

    @EventListener(ApplicationReadyEvent.class)
    void warnIfLicenseEnforcementDisabled() {
        if (!licenseEnforcementProperty.enforcementEnabled()) {
            log.warn("[ License ] drive.license.enforcement-enabled=false — 라이선스 검사 꺼짐(로컬 개발 전용). 운영에서는 반드시 true");
        }
    }

    public FileSizeLimitResponse getFileSizeLimit() {
        var dto = systemConfigFindService.findFileSizeLimitConfig(TenantContext.getOrThrow());
        return configResponseConverter.toFileSizeLimitResponse(dto);
    }

    public OfficeResponse getOfficeConfig(){
        var dto = systemConfigFindService.findOfficeConfig(TenantContext.getOrThrow());
        return configResponseConverter.toOfficeResponse(dto);
    }

    public ClientConfigResponse getClientConfig() {
        int tenantSeq = TenantContext.getOrThrow();
        // 슈퍼어드민 전체적용 중이면 글로벌 shareStatus, 아니면 테넌트 shareStatus 반환
        boolean shareStatus = shareSettingFacade.resolve(tenantSeq).shareEnabled();
        var dto = systemConfigFindService.findClientConfig(tenantSeq, shareStatus);
        int tenantCount = tenantFindService.countActiveSelfManagedTenants();
        return configResponseConverter.toClientConfigResponse(dto, tenantCount);
    }

    /** 요청 테넌트의 유형·사용자 모드 — 화면의 버튼 on/off 용(거부 판정은 TenantPolicy) */
    public TenantConfigResponse getTenantConfig() {
        var tenant = tenantFindService.findByTenantSeq(TenantContext.getOrThrow());
        return new TenantConfigResponse(tenant.getTenantType(), tenant.getUserMode());
    }

    /**
     * 라이선스 상태(화면 표시용). 등록된 라이선스가 없으면 null — 응답 매핑(404·400·500)은 컨트롤러가 한다.
     * {@code LicenseException}(런타임)은 그대로 던진다.
     */
    public Map<String, Object> getLicenseStatus() throws LicenseValidatorException {
        if (!licenseEnforcementProperty.enforcementEnabled()) {
            return LICENSE_ENFORCEMENT_DISABLED_STATUS;
        }
        int tenantSeq = TenantContext.getOrThrow();
        Map<String, Object> licenseMap = licenseService.getLicense(tenantSeq);
        if (licenseMap == null) {
            return null;
        }

        Map<String, Object> licenseStateMap = null;

        if (licenseMap.get("LICENSE_TYPE").equals(Constants.LICENSE.LICENSE_TYPE_SEAT)) {
            licenseStateMap = seatLicenseService.getLicenseStateMap(tenantSeq);
        } else {
            licenseStateMap = licenseService.getLicenseStateMap(tenantSeq);
        }

        // 화면 표시용 라이선스 데이터 변환 및 추가
        int currentActiverUserNum = seatLicenseService.getCurrentActiveUserNum(tenantSeq);
        return licenseService.convertLicenseForDisplay(licenseStateMap, currentActiverUserNum);
    }

}
