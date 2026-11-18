package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.*;
import com.thinkfree.drive.tenant.service.*;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.service.*;
import com.thinkfree.drive.systemconfig.service.*;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.storage.service.StorageAdminService;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagedSingleQuotaTest {
    @Mock TenantService tenantService;
    @Mock TenantFindService tenantFindService;
    @Mock UserService userService;
    @Mock UserFindService userFindService;
    @Mock SystemConfigService systemConfigService;
    @Mock SystemConfigFindService systemConfigFindService;
    @Mock QuotaService quotaService;
    @Mock StorageAdminService storageAdminService;
    @InjectMocks SuperAdminTenantUseCase useCase;
    User user;
    UserDetailData detail;

    @BeforeEach void setup() {
        user = User.builder().userSeq(20).tenantSeq(7).isProtected(true)
                .totalQuotaByte(100L).useQuotaByte(30L).build();
        detail = mock(UserDetailData.class);
        lenient().when(detail.getUserSeq()).thenReturn(20);
        lenient().when(detail.getUseQuotaByte()).thenReturn(30L);
        when(tenantFindService.findByTenantSeq(7)).thenReturn(TenantData.builder()
                .tenantSeq(7).tenantType(TenantType.SYSTEM_MANAGED).userMode(TenantUserMode.SINGLE).build());
        lenient().when(userFindService.findUndeletedDetailsByTenantSeq(7)).thenReturn(List.of(detail));
        lenient().when(userService.lockUserBySeq(20)).thenReturn(user);
        lenient().when(systemConfigFindService.findTenantTotalQuotaByte(7)).thenReturn(100L);
        lenient().when(userFindService.sumTotalQuotaByteByTenantSeq(7)).thenReturn(100L);
        lenient().when(quotaService.canIncrease(anyLong())).thenReturn(true);
    }
    void change(long bytes) { ReflectionTestUtils.invokeMethod(useCase, "changeTotalQuota", 7, bytes, 1); }
    @Test void increaseUpdatesSoleUserAndTenant() {
        change(200); assertThat(user.getTotalQuotaByte()).isEqualTo(200);
        verify(systemConfigService).upsertTenantTotalQuotaByte(7,200,1);
        var order=inOrder(tenantService,userService); order.verify(tenantService).lock(7); order.verify(userService).lockUserBySeq(20);
    }
    @Test void decreaseToActualUsageIgnoresOldAllocation() {
        change(30); assertThat(user.getTotalQuotaByte()).isEqualTo(30);
        verify(systemConfigService).upsertTenantTotalQuotaByte(7,30,1);
        verifyNoInteractions(quotaService);
    }
    @Test void equalTenantLimitRepairsOldUserAllocationAndIsRepeatable() {
        user.changeTotalQuota(40); change(100); change(100);
        assertThat(user.getTotalQuotaByte()).isEqualTo(100);
        verify(systemConfigService,times(2)).upsertTenantTotalQuotaByte(7,100,1);
    }
    @Test void rejectsBelowUsageAndZeroWithoutWrites() {
        assertThatThrownBy(()->change(29)).isInstanceOf(ApiRuntimeException.class);
        assertThatThrownBy(()->change(0)).isInstanceOf(ApiRuntimeException.class);
        assertThat(user.getTotalQuotaByte()).isEqualTo(100); verifyNoInteractions(systemConfigService);
    }
    @Test void capacityFailureDoesNotChangeEitherLimit() {
        when(quotaService.canIncrease(100)).thenReturn(false);
        assertThatThrownBy(()->change(200)).isInstanceOf(ApiRuntimeException.class);
        assertThat(user.getTotalQuotaByte()).isEqualTo(100); verifyNoInteractions(systemConfigService);
    }
    @Test void inconsistentSingleTenantFailsClosed() {
        when(userFindService.findUndeletedDetailsByTenantSeq(7)).thenReturn(List.of());
        assertThatThrownBy(()->change(200)).isInstanceOf(ApiRuntimeException.class);
        when(userFindService.findUndeletedDetailsByTenantSeq(7)).thenReturn(List.of(detail,detail));
        assertThatThrownBy(()->change(200)).isInstanceOf(ApiRuntimeException.class);
        verifyNoInteractions(systemConfigService,userService);
    }
    @Test void wrongOrUnprotectedAccountIsRejected() {
        when(userService.lockUserBySeq(20)).thenReturn(User.builder().tenantSeq(8).isProtected(true).build());
        assertThatThrownBy(()->change(200)).isInstanceOf(ApiRuntimeException.class);
        when(userService.lockUserBySeq(20)).thenReturn(User.builder().tenantSeq(7).isProtected(false).build());
        assertThatThrownBy(()->change(200)).isInstanceOf(ApiRuntimeException.class);
        verifyNoInteractions(systemConfigService);
    }
    @Test void otherTenantTypesKeepAllocationFloor() {
        for (var type : TenantType.values()) {
            when(tenantFindService.findByTenantSeq(7)).thenReturn(TenantData.builder().tenantType(type).userMode(TenantUserMode.MULTI).build());
            assertThatThrownBy(()->change(50)).isInstanceOf(ApiRuntimeException.class);
        }
        verifyNoInteractions(userService,systemConfigService);
    }
    @Test void capacityResponsePreservesAllocationAndSeparatelyReportsUsageFloor() {
        when(storageAdminService.getStorageCapacity()).thenReturn(StorageCapacityResult.unlimited());
        var response=useCase.findQuotaCapacity(7);
        assertThat(response.tenantAllocatedQuotaByte()).isEqualTo(100);
        assertThat(response.tenantMinimumQuotaByte()).isEqualTo(30);
    }
}
