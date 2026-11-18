package com.thinkfree.drive.application.quota;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.systemconfig.exception.SystemConfigErrorCode;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 사용자 할당량 배정 흐름 — 테넌트 행 잠금 → 총 용량(키 없으면 예외)·할당 합 조회 → {@code TenantQuotaPolicy} 판정.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantUserQuotaGuard — 사용자 할당량 배정 × 테넌트 총 용량")
class TenantUserQuotaGuardTest {

    private static final int TENANT = 7;

    @Mock
    private TenantService tenantService;
    @Mock
    private SystemConfigFindService systemConfigFindService;
    @Mock
    private UserFindService userFindService;

    @InjectMocks
    private TenantUserQuotaGuard guard;

    @Test
    @DisplayName("증감분 0 이하(감소·동일)는 잠금·조회 없이 통과")
    void nonPositiveDelta_skips() {
        guard.ensureAssignable(TENANT, 0L);
        guard.ensureAssignable(TENANT, -100L);

        verifyNoInteractions(tenantService, systemConfigFindService, userFindService);
    }

    @Test
    @DisplayName("잠근 뒤 할당 합 + 증감분이 총 용량과 같으면 통과, 넘으면 TENANT_QUOTA_EXCEEDED")
    void checksTenantTotal() {
        when(systemConfigFindService.findTenantTotalQuotaByteOrThrow(TENANT)).thenReturn(100L);
        when(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).thenReturn(60L);

        guard.ensureAssignable(TENANT, 40L);
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> guard.ensureAssignable(TENANT, 41L));

        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        verify(tenantService, times(2)).lock(TENANT);
    }

    @Test
    @DisplayName("테넌트 총 용량 키가 없으면 기본값 없이 TENANT_TOTAL_QUOTA_NOT_FOUND")
    void keyMissing_throws() {
        when(systemConfigFindService.findTenantTotalQuotaByteOrThrow(TENANT))
                .thenThrow(ApiRuntimeException.type(SystemConfigErrorCode.TENANT_TOTAL_QUOTA_NOT_FOUND));

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> guard.ensureAssignable(TENANT, 1L));

        assertThat(e.getCode()).isEqualTo(SystemConfigErrorCode.TENANT_TOTAL_QUOTA_NOT_FOUND);
    }

    @Test
    @DisplayName("ensureAssignableLocked — 이미 잠근 흐름용이라 다시 잠그지 않고 판정만(같으면 통과, 넘으면 TENANT_QUOTA_EXCEEDED, 0 이하는 조회 없이 통과)")
    void locked_doesNotRelock() {
        when(systemConfigFindService.findTenantTotalQuotaByteOrThrow(TENANT)).thenReturn(100L);
        when(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).thenReturn(60L);

        guard.ensureAssignableLocked(TENANT, 40L);
        guard.ensureAssignableLocked(TENANT, 0L);
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> guard.ensureAssignableLocked(TENANT, 41L));

        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("lockTenant 는 테넌트 행만 잠근다(사용자 행보다 먼저 부르는 용도)")
    void lockTenant() {
        guard.lockTenant(TENANT);

        verify(tenantService).lock(TENANT);
        verifyNoInteractions(systemConfigFindService, userFindService);
    }
}
