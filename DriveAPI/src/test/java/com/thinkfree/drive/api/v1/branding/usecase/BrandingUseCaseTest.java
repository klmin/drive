package com.thinkfree.drive.api.v1.branding.usecase;

import com.thinkfree.drive.api.v1.branding.response.BrandingMetaResponse;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.properties.BrandingProperty;
import com.thinkfree.drive.branding.service.BrandingFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.result.LicensePlanResult;
import com.thinkfree.drive.license.service.LicenseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 테넌트가 특정되지 않은 요청(슈퍼어드민 포털, {@code SecurityPaths.TENANT_OPTIONAL})에서 TenantContext 가 비어 있을 때
 * 기본 브랜딩을 돌려주는지. 테넌트가 있을 때의 동작(라이선스 플랜·화이트라벨)은 종전과 같다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrandingUseCase — 테넌트 없음/있음")
class BrandingUseCaseTest {

    private static final int TENANT = 7;

    @Mock
    private BrandingFindService brandingFindService;
    @Mock
    private LicenseService licenseService;

    private BrandingUseCase useCase;

    @BeforeEach
    void setUp() {
        BrandingProperty property = new BrandingProperty(new BrandingProperty.Defaults("기본 탭", "기본 서비스"));
        useCase = new BrandingUseCase(brandingFindService, property, licenseService);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext 가 비어 있으면 yml 기본값만 반환하고 라이선스·브랜딩 조회를 하지 않는다(plan·category null)")
    void returnsDefaultsWithoutTenant() {
        BrandingMetaResponse response = useCase.getMeta();

        assertThat(response.tabTitle()).isEqualTo("기본 탭");
        assertThat(response.serviceName()).isEqualTo("기본 서비스");
        assertThat(response.plan()).isNull();
        assertThat(response.category()).isNull();
        verifyNoInteractions(brandingFindService, licenseService);
    }

    @Test
    @DisplayName("TenantContext 가 있으면 종전대로 그 테넌트의 라이선스 플랜을 붙인다(브랜딩 미등록 → 기본 문구 + 플랜)")
    void usesTenantPlanWhenTenantPresent() {
        TenantContext.set(TENANT);
        when(licenseService.getLicensePlan(TENANT)).thenReturn(new LicensePlanResult("PAID", "ENTERPRISE"));
        when(brandingFindService.findMetaOrThrow(TENANT)).thenThrow(ApiRuntimeException.type(BrandingErrorCode.BRANDING_NOT_FOUND));

        BrandingMetaResponse response = useCase.getMeta();

        assertThat(response.tabTitle()).isEqualTo("기본 탭");
        assertThat(response.serviceName()).isEqualTo("기본 서비스");
        assertThat(response.plan()).isEqualTo("ENTERPRISE");
        assertThat(response.category()).isEqualTo("PAID");
    }
}
