package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.storage.enums.CapacityMode;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 슈퍼어드민 테넌트 등록·수정 모달의 총 용량 입력 검증용 조회 — 등록(tenantSeq 없음)·수정·스토리지 종류·Ceph 호출 실패.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminTenantUseCase — 총 용량 입력 검증용 용량 조회")
class SuperAdminTenantUseCaseQuotaCapacityTest {

    private static final int TENANT = 7;

    @Mock
    private TenantFindService tenantFindService;
    @Mock
    private SystemConfigFindService systemConfigFindService;
    @Mock
    private UserFindService userFindService;
    @Mock
    private StorageAdminService storageAdminService;

    @InjectMocks
    private SuperAdminTenantUseCase useCase;

    @Test
    @DisplayName("등록(tenantSeq 없음) + Ceph — 스토리지 값만, 테넌트 값 null")
    void create_ceph() {
        when(storageAdminService.getStorageCapacity())
                .thenReturn(new StorageCapacityResult(CapacityMode.LIMITED, 400L, 1_000L, 600L));

        var response = useCase.findQuotaCapacity(null);

        assertThat(response.storageTotalByte()).isEqualTo(1_000L);
        assertThat(response.storageAvailByte()).isEqualTo(400L);
        assertThat(response.tenantTotalQuotaByte()).isNull();
        assertThat(response.tenantAllocatedQuotaByte()).isNull();
        verifyNoInteractions(tenantFindService, systemConfigFindService, userFindService);
    }

    @Test
    @DisplayName("수정 + Ceph — 테넌트 총 용량·할당 합계 포함")
    void update_ceph() {
        when(tenantFindService.findByTenantSeq(TENANT)).thenReturn(com.thinkfree.drive.tenant.data.TenantData.builder().build());
        when(systemConfigFindService.findTenantTotalQuotaByte(TENANT)).thenReturn(200L);
        when(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).thenReturn(150L);
        when(storageAdminService.getStorageCapacity())
                .thenReturn(new StorageCapacityResult(CapacityMode.LIMITED, 400L, 1_000L, 600L));

        var response = useCase.findQuotaCapacity(TENANT);

        verify(tenantFindService).findByTenantSeq(TENANT);
        assertThat(response.tenantTotalQuotaByte()).isEqualTo(200L);
        assertThat(response.tenantAllocatedQuotaByte()).isEqualTo(150L);
        assertThat(response.storageAvailByte()).isEqualTo(400L);
    }

    @Test
    @DisplayName("수정 — 총 용량 키 누락이면 tenantTotalQuotaByte 만 null")
    void update_missingTotalQuotaKey() {
        when(tenantFindService.findByTenantSeq(TENANT)).thenReturn(com.thinkfree.drive.tenant.data.TenantData.builder().build());
        when(systemConfigFindService.findTenantTotalQuotaByte(TENANT)).thenReturn(null);
        when(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).thenReturn(0L);
        when(storageAdminService.getStorageCapacity()).thenReturn(StorageCapacityResult.unlimited());

        var response = useCase.findQuotaCapacity(TENANT);

        assertThat(response.tenantTotalQuotaByte()).isNull();
        assertThat(response.tenantAllocatedQuotaByte()).isZero();
    }

    @Test
    @DisplayName("S3·RustFS(UNLIMITED) — 스토리지 값 null")
    void unlimited() {
        when(storageAdminService.getStorageCapacity()).thenReturn(StorageCapacityResult.unlimited());

        var response = useCase.findQuotaCapacity(null);

        assertThat(response.storageTotalByte()).isNull();
        assertThat(response.storageAvailByte()).isNull();
    }

    @Test
    @DisplayName("Ceph 호출 실패 — ExternalApiException 그대로 전파")
    void ceph_fails() {
        when(storageAdminService.getStorageCapacity()).thenThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));

        var e = catchThrowableOfType(ExternalApiException.class, () -> useCase.findQuotaCapacity(null));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(userFindService, never()).sumTotalQuotaByteByTenantSeq(anyInt());
    }
}
