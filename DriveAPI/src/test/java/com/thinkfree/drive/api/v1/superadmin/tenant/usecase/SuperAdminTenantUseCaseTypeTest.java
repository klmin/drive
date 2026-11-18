package com.thinkfree.drive.api.v1.superadmin.tenant.usecase;

import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.api.v1.superadmin.tenant.converter.SuperAdminTenantRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.tenant.converter.SuperAdminTenantRequestConverterImpl;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.auditlog.service.SettingAuditService;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.cleanup.tenant.service.TenantDeletionService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.share.setting.service.ShareSettingService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.dto.TenantSaveDto;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.tenant.service.TenantService;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.dto.UserSaveResult;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 슈퍼어드민 테넌트 등록·수정의 유형(tt)·사용자 모드(um) 분기 — SYSTEM_MANAGED 는 관리자 계정 USER+ADMIN·총 용량, ips 무시, 공유 off.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminTenantUseCase — 테넌트 유형·사용자 모드")
class SuperAdminTenantUseCaseTypeTest {

    private static final int NEW_TENANT = 7;
    private static final int NEW_USER = 70;
    private static final int LOGIN_USER = 1;
    private static final long TQ = 1_000L;
    private static final List<String> IPS = List.of("10.0.0.1");

    @Mock
    private QuotaService quotaService;
    @Mock
    private TenantService tenantService;
    @Mock
    private TenantFindService tenantFindService;
    @Mock
    private BaseDomainFindService baseDomainFindService;
    @Mock
    private UserService userService;
    @Mock
    private AccessAllowedIpService accessAllowedIpService;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private SystemConfigFindService systemConfigFindService;
    @Mock
    private ShareSettingService shareSettingService;
    @Mock
    private UserFindService userFindService;
    @Mock
    private SettingAuditService settingAuditService;
    @Mock
    private TenantDeletionService tenantDeletionService;
    @Spy
    private SuperAdminTenantRequestConverter requestConverter = new SuperAdminTenantRequestConverterImpl();

    @InjectMocks
    private SuperAdminTenantUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(quotaService.canIncrease(anyLong())).thenReturn(true);
        lenient().when(baseDomainFindService.findByBaseDomainSeqOrThrow(1)).thenReturn(new BaseDomainData(1, "drive.example.com"));
        lenient().when(tenantService.save(any())).thenReturn(NEW_TENANT);
        lenient().when(userService.registerTenantAdmin(any())).thenReturn(new UserSaveResult(NEW_USER));
    }

    private static SuperAdminTenantCreateRequest createRequest(TenantType tt, TenantUserMode um) {
        return new SuperAdminTenantCreateRequest("t", "sd", 1, "a@b.c", "n", "Test1234!", TQ, IPS, tt, um);
    }

    @Test
    @DisplayName("tt·um 생략 — SELF_MANAGED·MULTI, 관리자 ADMIN 만·용량 0·보호 계정, ips 저장, 공유 기본값, 응답 seq")
    void create_default_selfManagedMulti() {
        var response = useCase.create(createRequest(null, null), LOGIN_USER);

        var saveDto = ArgumentCaptor.forClass(TenantSaveDto.class);
        verify(tenantService).save(saveDto.capture());
        assertThat(saveDto.getValue().getTenantType()).isEqualTo(TenantType.SELF_MANAGED);
        assertThat(saveDto.getValue().getUserMode()).isEqualTo(TenantUserMode.MULTI);

        var adminDto = ArgumentCaptor.forClass(AdminUserSaveDto.class);
        verify(userService).registerTenantAdmin(adminDto.capture());
        assertThat(adminDto.getValue().roles()).isEqualTo(Set.of(RoleType.ADMIN));
        assertThat(adminDto.getValue().totalQuotaByte()).isZero();

        verify(accessAllowedIpService).replaceAllowedIps(NEW_TENANT, RoleType.ADMIN, LOGIN_USER, IPS);
        verify(shareSettingService).initializeDefaultForTenant(NEW_TENANT);
        verify(shareSettingService, never()).upsertTenantShareOff(anyInt());
        assertThat(response.tenantSeq()).isEqualTo(NEW_TENANT);
        assertThat(response.userSeq()).isEqualTo(NEW_USER);
    }

    @Test
    @DisplayName("SYSTEM_MANAGED·SINGLE — 관리자 USER+ADMIN·총 용량 전부, ips 무시, 공유 off(share_status=false)")
    void create_systemManaged() {
        var response = useCase.create(createRequest(TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE), LOGIN_USER);

        var saveDto = ArgumentCaptor.forClass(TenantSaveDto.class);
        verify(tenantService).save(saveDto.capture());
        assertThat(saveDto.getValue().getTenantType()).isEqualTo(TenantType.SYSTEM_MANAGED);
        assertThat(saveDto.getValue().getUserMode()).isEqualTo(TenantUserMode.SINGLE);

        var adminDto = ArgumentCaptor.forClass(AdminUserSaveDto.class);
        verify(userService).registerTenantAdmin(adminDto.capture());
        assertThat(adminDto.getValue().roles()).isEqualTo(Set.of(RoleType.USER, RoleType.ADMIN));
        assertThat(adminDto.getValue().totalQuotaByte()).isEqualTo(TQ);

        verify(accessAllowedIpService, never()).replaceAllowedIps(any(), any(), any(), any());
        verify(shareSettingService).upsertTenantShareOff(NEW_TENANT);
        verify(systemConfigService).upsertShareStatus(NEW_TENANT, false, LOGIN_USER);
        assertThat(response.userSeq()).isEqualTo(NEW_USER);
    }

    @Test
    @DisplayName("SELF_MANAGED + SINGLE — TENANT_009, 아무것도 만들지 않음")
    void create_selfManagedSingle_rejected() {
        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> useCase.create(createRequest(TenantType.SELF_MANAGED, TenantUserMode.SINGLE), LOGIN_USER));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.TENANT_USER_MODE_NOT_ALLOWED);
        verify(tenantService, never()).save(any());
        verify(userService, never()).registerTenantAdmin(any());
    }

    @Test
    @DisplayName("수정 — SYSTEM_MANAGED 는 ips 무시(허용 IP 저장·감사 기록 없음)")
    void update_systemManaged_ignoresIps() {
        when(systemConfigFindService.findTenantTotalQuotaByte(NEW_TENANT)).thenReturn(TQ);
        when(tenantFindService.findByTenantSeq(NEW_TENANT))
                .thenReturn(TenantData.builder().tenantSeq(NEW_TENANT).tenantType(TenantType.SYSTEM_MANAGED).build());

        useCase.update(NEW_TENANT, new SuperAdminTenantUpdateRequest("t", "sd", 1, TQ, IPS), LOGIN_USER);

        verify(tenantService).update(any());
        verify(accessAllowedIpService, never()).replaceAllowedIps(any(), any(), any(), any());
        verify(settingAuditService, never()).recordAllowedIpChangeIfNeeded(any(), any());
    }

    @Test
    @DisplayName("수정 — SELF_MANAGED 는 ips 저장(기존 동작)")
    void update_selfManaged_replacesIps() {
        when(systemConfigFindService.findTenantTotalQuotaByte(NEW_TENANT)).thenReturn(TQ);
        when(tenantFindService.findByTenantSeq(NEW_TENANT))
                .thenReturn(TenantData.builder().tenantSeq(NEW_TENANT).tenantType(TenantType.SELF_MANAGED).build());

        useCase.update(NEW_TENANT, new SuperAdminTenantUpdateRequest("t", "sd", 1, TQ, IPS), LOGIN_USER);

        verify(accessAllowedIpService).replaceAllowedIps(NEW_TENANT, RoleType.ADMIN, LOGIN_USER, IPS);
    }

    @Test
    @DisplayName("삭제 — SYSTEM_MANAGED 는 삭제 서비스 호출")
    void delete_systemManaged() {
        when(tenantFindService.findByTenantSeq(NEW_TENANT))
                .thenReturn(TenantData.builder().tenantSeq(NEW_TENANT).tenantType(TenantType.SYSTEM_MANAGED).build());

        useCase.delete(NEW_TENANT, LOGIN_USER);

        verify(tenantDeletionService).deleteTenant(NEW_TENANT, LOGIN_USER);
    }

    @Test
    @DisplayName("삭제 — SELF_MANAGED 는 TENANT_010, 아무것도 지우지 않음")
    void delete_selfManaged_rejected() {
        when(tenantFindService.findByTenantSeq(NEW_TENANT))
                .thenReturn(TenantData.builder().tenantSeq(NEW_TENANT).tenantType(TenantType.SELF_MANAGED).build());

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> useCase.delete(NEW_TENANT, LOGIN_USER));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.TENANT_DELETE_NOT_ALLOWED);
        verify(tenantDeletionService, never()).deleteTenant(anyInt(), anyInt());
    }
}
