package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 슈퍼어드민 테넌트 등록·수정의 총 용량 흐름 — 스토리지(Ceph) 대시보드 호출 실패는 가용량 부족(QUOTA_004)으로, 원인은 context 에.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminTenantUseCase — 테넌트 총 용량 스토리지 확인 실패 처리")
class SuperAdminTenantUseCaseQuotaTest {

    private static final int TENANT = 7;
    private static final int LOGIN_USER = 1;

    @Mock
    private QuotaService quotaService;
    @Mock
    private TenantService tenantService;
    @Mock
    private SystemConfigFindService systemConfigFindService;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private UserFindService userFindService;

    @Mock
    private com.thinkfree.drive.tenant.service.TenantFindService tenantFindService;

    @InjectMocks
    private SuperAdminTenantUseCase useCase;

    @Test
    @DisplayName("등록 — Ceph 호출 실패면 QUOTA_004(원인 context)·테넌트 INSERT 없음")
    void create_storageAdminFails_throwsCapacityInsufficient() {
        when(quotaService.canIncrease(1_000L)).thenThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));
        var request = new SuperAdminTenantCreateRequest("t", "sd", 1, "a@b.c", "n", "p", 1_000L, List.of(), null, null);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> useCase.create(request, LOGIN_USER));

        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        assertThat(e.getContext()).containsKey("storageAdminError");
        verify(tenantService, never()).save(any());
    }

    @Test
    @DisplayName("수정 — 증가분 Ceph 호출 실패면 QUOTA_004·총 용량 저장 없음")
    void update_storageAdminFails_throwsCapacityInsufficient() {
        when(tenantFindService.findByTenantSeq(TENANT)).thenReturn(com.thinkfree.drive.tenant.data.TenantData.builder().build());
        when(systemConfigFindService.findTenantTotalQuotaByte(TENANT)).thenReturn(100L);
        when(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).thenReturn(0L);
        when(quotaService.canIncrease(50L)).thenThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY));
        var request = new SuperAdminTenantUpdateRequest("t", "sd", 1, 150L, List.of());

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> useCase.update(TENANT, request, LOGIN_USER));

        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        verify(tenantService).lock(TENANT);
        verify(systemConfigService, never()).upsertTenantTotalQuotaByte(anyInt(), anyLong(), anyInt());
    }
}
