package com.thinkfree.drive.api.v1.config.usecase;

import com.thinkfree.drive.api.v1.config.converter.ConfigResponseConverter;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.properties.LicenseEnforcementProperty;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.share.setting.facade.ShareSettingFacade;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

/** 라이선스 검사 스위치(drive.license.enforcement-enabled) — 끄면 등록 여부와 상관없이 사용 가능 상태 */
@ExtendWith(MockitoExtension.class)
class ConfigUseCaseLicenseEnforcementTest {

    @Mock SystemConfigFindService systemConfigFindService;
    @Mock TenantFindService tenantFindService;
    @Mock ConfigResponseConverter configResponseConverter;
    @Mock ShareSettingFacade shareSettingFacade;
    @Mock LicenseService licenseService;
    @Mock SeatLicenseService seatLicenseService;

    private ConfigUseCase useCase(boolean enforcementEnabled) {
        return new ConfigUseCase(systemConfigFindService, tenantFindService, configResponseConverter, shareSettingFacade,
                licenseService, seatLicenseService, new LicenseEnforcementProperty(enforcementEnabled));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void 검사를_끄면_라이선스를_조회하지_않고_사용_가능_상태를_돌려준다() throws Exception {
        Map<String, Object> status = useCase(false).getLicenseStatus();

        assertThat(status).containsEntry("state", true)
                .containsEntry("paidWhiteLabel", false)
                .containsEntry("enforcementDisabled", true);
        verifyNoInteractions(licenseService, seatLicenseService);
    }

    @Test
    void 검사를_켜면_미등록은_그대로_null_이다() throws Exception {
        TenantContext.set(1);
        given(licenseService.getLicense(1)).willReturn(null);

        assertThat(useCase(true).getLicenseStatus()).isNull();
        then(licenseService).should().getLicense(1);
    }
}
