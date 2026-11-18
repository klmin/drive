package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.systemconfig.data.OfficeData;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 슈퍼관리자 테넌트 Office 설정 조회 — 등록·수정 화면이 현재 값을 채운다 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminTenantUseCase — Office 설정 조회")
class SuperAdminTenantUseCaseOfficeTest {

    private static final int TENANT = 7;

    @Mock
    private TenantFindService tenantFindService;
    @Mock
    private SystemConfigFindService systemConfigFindService;
    @InjectMocks
    private SuperAdminTenantUseCase useCase;

    @Test
    @DisplayName("서비스 관리여도 토큰·서명키를 그대로 준다 — PUT 이 서명키를 매번 받으므로 수정 화면이 채워야 한다")
    void getOffice_returnsSecretsAndServiceManaged() {
        when(systemConfigFindService.findOfficeConfig(TENANT)).thenReturn(OfficeData.builder()
                .officeConnectionType("onPremise")
                .officeDomain("https://office.example.com")
                .officeAuthMode("ticket")
                .officeTicketServiceToken("token")
                .officeHttpStorageAdapterName("7")
                .officeHttpStorageRequestJwtSecret("s".repeat(32))
                .build());
        when(systemConfigFindService.isOfficeServiceManaged(TENANT)).thenReturn(true);

        var office = useCase.getOffice(TENANT);

        verify(tenantFindService).findByTenantSeq(TENANT);
        assertThat(office.officeConnectionType()).isEqualTo("onPremise");
        assertThat(office.officeDomain()).isEqualTo("https://office.example.com");
        assertThat(office.officeAuthMode()).isEqualTo("ticket");
        assertThat(office.officeTicketServiceToken()).isEqualTo("token");
        assertThat(office.officeHttpStorageAdapterName()).isEqualTo("7");
        assertThat(office.officeHttpStorageRequestJwtSecret()).isEqualTo("s".repeat(32));
        assertThat(office.serviceManaged()).isTrue();
    }

    @Test
    @DisplayName("없는 테넌트면 TENANT_NOT_FOUND — 설정은 읽지 않는다")
    void getOffice_whenTenantMissing_throwsNotFound() {
        when(tenantFindService.findByTenantSeq(anyInt()))
                .thenThrow(ApiRuntimeException.type(TenantErrorCode.TENANT_NOT_FOUND));

        assertThatThrownBy(() -> useCase.getOffice(TENANT)).isInstanceOf(ApiRuntimeException.class);
        verifyNoInteractions(systemConfigFindService);
    }
}
