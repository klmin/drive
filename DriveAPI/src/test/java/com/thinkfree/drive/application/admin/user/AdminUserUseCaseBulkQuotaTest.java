package com.thinkfree.drive.application.admin.user;

import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.admin.user.converter.AdminUserCommandConverter;
import com.thinkfree.drive.application.admin.user.converter.AdminUserInfoConverter;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.tenant.TenantValidator;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.LicenseService.SeatPolicy;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 일괄 등록/수정의 역할·할당량 규칙 — 역할(USER/ADMIN 1개 이상) 판정 → (수정 항목 roles 지정 시) 역할 교체 위임 →
 * 보유 역할별 할당량 확정(ADMIN 만이면 0·생략 시 0, USER 보유면 필수) →
 * 테넌트 총 용량 판정(신규 합 + 수정 증감 합, 감소분 상쇄) 순서를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserImportUseCase.bulkUpsert — 역할·할당량 규칙")
class AdminUserUseCaseBulkQuotaTest {

    private static final int TENANT = 1;
    private static final int LOGIN_USER = 1;
    private static final Set<RoleType> USER_ONLY = Set.of(RoleType.USER);
    private static final Set<RoleType> ADMIN_ONLY = Set.of(RoleType.ADMIN);
    private static final Set<RoleType> USER_ADMIN = Set.of(RoleType.USER, RoleType.ADMIN);

    @Mock private AdminUserCommandConverter adminUserCommandConverter;
    @Mock private AdminUserInfoConverter adminUserInfoConverter;
    @Mock private UserService userService;
    @Mock private TenantPolicy tenantPolicy;
    @Mock private UserFindService userFindService;
    @Mock private LicenseService licenseService;
    @Mock private SeatLicenseService seatLicenseService;
    @Mock private QuotaService quotaService;
    @Mock private TenantUserQuotaGuard tenantUserQuotaGuard;
    @Mock private AdminUserService adminUserService;
    @Mock private TenantValidator tenantValidator;
    @Mock private UserAuditLookupService userAuditLookupService;
    @Mock private UserAuditService userAuditService;
    @Mock private UserRoleService userRoleService;
    @Mock private UserRoleFindService userRoleFindService;

    /** 수정 대상의 보유 역할(tb_user_role) — current() 가 채운다 */
    private final Map<Integer, Set<RoleType>> heldRoles = new HashMap<>();

    @InjectMocks
    private AdminUserImportUseCase adminUserImportUseCase;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(userFindService.findExistingUserIds(anyInt(), anyList())).willReturn(Set.of());
        given(licenseService.getSeatPolicy(anyInt())).willReturn(new SeatPolicy(false, 0));
        given(quotaService.canIncrease(anyLong())).willReturn(true);
        given(adminUserService.upsertAll(anyList(), anyList(), any())).willReturn(List.of());
        given(userAuditLookupService.findUser(anyInt())).willReturn(Mockito.mock(UserDetailData.class)); // 수정 전 스냅샷(toMap 은 null 값 불가)
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UserItemCommand insert(String userId, Long tq, Set<RoleType> roles) {
        return new UserItemCommand(null, userId, "Passw0rd!x", "name", tq, roles, null, null);
    }

    private static UserItemCommand update(int userSeq, Long tq) {
        return update(userSeq, tq, null);
    }

    private static UserItemCommand update(int userSeq, Long tq, Set<RoleType> roles) {
        return new UserItemCommand(userSeq, null, null, "name", tq, roles, null, null);
    }

    private User current(int userSeq, Set<RoleType> roles, long totalQuotaByte) {
        return current(userSeq, roles, totalQuotaByte, 0L);
    }

    private User current(int userSeq, Set<RoleType> roles, long totalQuotaByte, long useQuotaByte) {
        heldRoles.put(userSeq, roles);
        User user = Mockito.mock(User.class);
        given(user.getUserSeq()).willReturn(userSeq);
        given(user.getTotalQuotaByte()).willReturn(totalQuotaByte);
        given(user.getUseQuotaByte()).willReturn(useQuotaByte);
        given(user.isInvalidTotalQuotaChange(anyLong())).willReturn(false);
        return user;
    }

    /** 수정 대상 — 잠금 전 조회(스토리지 판정용 DTO)와 잠금 조회(엔티티)가 같은 값을 돌려준다 */
    private void targets(List<User> users) {
        List<UserDetailData> details = users.stream().map(u -> {
            UserDetailData d = Mockito.mock(UserDetailData.class);
            Integer seq = u.getUserSeq();
            long total = u.getTotalQuotaByte();
            given(d.getUserSeq()).willReturn(seq);
            given(d.getTotalQuotaByte()).willReturn(total);
            long use = u.getUseQuotaByte();
            given(d.getUseQuotaByte()).willReturn(use);
            return d;
        }).toList();
        given(userFindService.findDetailsByUserSeqs(anyList())).willReturn(details);
        given(userService.lockUsersBySeqList(anyList())).willReturn(users);
        given(userRoleFindService.findRolesByUserSeqs(any())).willReturn(Map.copyOf(heldRoles));
    }

    private ApiRuntimeException bulkFails(UserItemCommand... items) {
        return catchThrowableOfType(ApiRuntimeException.class,
                () -> adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(items)), LOGIN_USER));
    }

    @Test
    @DisplayName("등록 역할이 USER/ADMIN 1개 이상이 아니면(SUPER_ADMIN·빈 목록) USER_028 — 용량 판정 전에 거부")
    void insert_unregistrableRole_rejected() {
        assertThat(bulkFails(insert("a@test.com", 1L, Set.of(RoleType.SUPER_ADMIN))).getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        assertThat(bulkFails(insert("a@test.com", 1L, Set.of())).getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        then(tenantUserQuotaGuard).should(never()).ensureAssignableLocked(anyInt(), anyLong());
    }

    @Test
    @DisplayName("한 요청 안의 같은 userId 는 DB 대조 전에 USER_004(INSERT 유니크 위반 500 방지), 중복 검사는 현재 테넌트 기준")
    void insert_duplicateInRequest_rejected() {
        assertThat(bulkFails(insert("d@test.com", 1L, USER_ONLY), insert("d@test.com", 1L, USER_ONLY)).getCode()).isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        then(userFindService).should(never()).findExistingUserIds(anyInt(), anyList());

        given(userFindService.findExistingUserIds(TENANT, List.of("e@test.com"))).willReturn(Set.of("e@test.com"));
        assertThat(bulkFails(insert("e@test.com", 1L, USER_ONLY)).getCode()).isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("ADMIN 등록에 양수 용량이면 QUOTA_009, USER 등록에 용량 누락이면 REQUIRED_FIELD·0 이면 QUOTA_008")
    void insert_quotaByRole_rejected() {
        assertThat(bulkFails(insert("a@test.com", 1L, ADMIN_ONLY)).getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);
        assertThat(bulkFails(insert("u@test.com", null, USER_ONLY)).getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);
        assertThat(bulkFails(insert("u@test.com", 0L, USER_ONLY)).getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("ADMIN 용량 생략은 0 으로 저장, 테넌트 총 용량은 USER 할당량 합만 판정")
    void insert_adminNullQuota_savedAsZero() {
        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(
                insert("admin@test.com", null, ADMIN_ONLY),
                insert("user@test.com", 5L, USER_ONLY))), LOGIN_USER);

        ArgumentCaptor<List<UserItemCommand>> saved = ArgumentCaptor.forClass(List.class);
        then(adminUserCommandConverter).should().toSaveDtos(saved.capture(), eq(LOGIN_USER), eq(TENANT));
        assertThat(saved.getValue()).extracting(UserItemCommand::totalQuotaByte).containsExactly(0L, 5L);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, 5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("수정 항목은 대상 계정 역할 기준 — ADMIN 생략은 0, 증감 합(감소분 상쇄)으로 테넌트 총 용량 판정")
    void update_byCurrentRole_netDelta() {
        User admin = current(10, ADMIN_ONLY, 0L);
        User user = current(20, USER_ONLY, 10L);
        targets(List.of(admin, user));

        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(
                update(10, null),               // 관리자: 생략 → 0 (증감 0)
                update(20, 4L),                 // 사용자: 10 → 4 (−6)
                insert("new@test.com", 5L, USER_ONLY))), LOGIN_USER); // +5

        ArgumentCaptor<List<UserItemCommand>> updated = ArgumentCaptor.forClass(List.class);
        then(adminUserCommandConverter).should().toBulkUpdateDtos(updated.capture());
        assertThat(updated.getValue()).extracting(UserItemCommand::totalQuotaByte).containsExactly(0L, 4L);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, -1L); // 5 − 6
    }

    @Test
    @DisplayName("스토리지(Ceph) 가용량 확인은 테넌트·사용자 잠금보다 먼저 — 늘어나는 수정분(새 값 − 사용량) + 신규 합")
    void storageCheckBeforeLocks() {
        User user = current(20, USER_ONLY, 10L);
        targets(List.of(user));

        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(
                update(20, 15L),                                   // 늘어남: 15 − 사용량 0 = 15
                insert("new@test.com", 5L, USER_ONLY))), LOGIN_USER); // 신규 5

        var order = Mockito.inOrder(quotaService, tenantUserQuotaGuard, userService);
        order.verify(quotaService).canIncrease(20L);
        order.verify(tenantUserQuotaGuard).lockTenant(TENANT);
        order.verify(userService).lockUsersBySeqList(anyList());
        order.verify(tenantUserQuotaGuard).ensureAssignableLocked(TENANT, 10L); // 5 + (15 − 10)
        then(tenantUserQuotaGuard).should(Mockito.times(1)).lockTenant(TENANT); // 좌석·총 용량·사용자 잠금 앞에서 테넌트 잠금은 한 번
    }

    @Test
    @DisplayName("수정 대상이 SUPER_ADMIN·ANONYMOUS(설치 시 계정)면 BULK_UPSERT_USER_NOT_FOUND — 용량 판정 전에 거부")
    void update_unregistrableTarget_rejected() {
        for (RoleType role : new RoleType[]{RoleType.SUPER_ADMIN, RoleType.ANONYMOUS}) {
            User target = current(30, Set.of(role), 0L);
            targets(List.of(target));
            assertThat(bulkFails(update(30, 0L)).getCode()).isEqualTo(UserErrorCode.BULK_UPSERT_USER_NOT_FOUND);
        }
        then(tenantUserQuotaGuard).should(never()).ensureAssignableLocked(anyInt(), anyLong());
    }

    @Test
    @DisplayName("수정 대상이 ADMIN 인데 양수 용량이면 QUOTA_009, USER 인데 누락이면 REQUIRED_FIELD")
    void update_quotaByCurrentRole_rejected() {
        User admin = current(10, ADMIN_ONLY, 0L);
        targets(List.of(admin));
        assertThat(bulkFails(update(10, 1L)).getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);

        User user = current(20, USER_ONLY, 10L);
        targets(List.of(user));
        assertThat(bulkFails(update(20, null)).getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);
        assertThat(bulkFails(update(20, 0L)).getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("USER·ADMIN 동시 등록 — USER 규칙(용량 필수), 저장 항목 roles 그대로")
    void insert_userAndAdmin() {
        assertThat(bulkFails(insert("ua@test.com", null, USER_ADMIN)).getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(insert("ua@test.com", 5L, USER_ADMIN))), LOGIN_USER);

        ArgumentCaptor<List<UserItemCommand>> saved = ArgumentCaptor.forClass(List.class);
        then(adminUserCommandConverter).should().toSaveDtos(saved.capture(), eq(LOGIN_USER), eq(TENANT));
        assertThat(saved.getValue()).extracting(UserItemCommand::roles).containsExactly(USER_ADMIN);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, 5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("수정 항목 roles 지정 — 잠근 사용량으로 교체 위임, 교체 후 역할로 용량 확정(USER 회수는 0), roles 생략은 현재 역할 유지")
    void update_roles_replaced() {
        User revoke = current(10, USER_ADMIN, 8L);         // USER 회수 → 관리자만, 용량 생략 → 0
        User grant = current(20, ADMIN_ONLY, 0L, 0L);      // USER 추가 → 용량 필수
        User keep = current(30, USER_ADMIN, 4L, 2L);       // roles 생략 → 유지
        targets(List.of(revoke, grant, keep));

        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(
                update(10, null, ADMIN_ONLY),
                update(20, 6L, USER_ADMIN),
                update(30, 4L))), LOGIN_USER);

        then(userRoleService).should().replaceRoles(new UserRoleChangeDto(10, ADMIN_ONLY, LOGIN_USER, 0L));
        then(userRoleService).should().replaceRoles(new UserRoleChangeDto(20, USER_ADMIN, LOGIN_USER, 0L));
        then(userRoleService).should(Mockito.times(2)).replaceRoles(any());

        ArgumentCaptor<List<UserItemCommand>> updated = ArgumentCaptor.forClass(List.class);
        then(adminUserCommandConverter).should().toBulkUpdateDtos(updated.capture());
        assertThat(updated.getValue()).extracting(UserItemCommand::totalQuotaByte).containsExactly(0L, 6L, 4L);
        // 저장 항목의 roles = 교체 후 보유 역할(생략 항목은 현재 역할) — tb_user.role_type 대표 역할의 근거
        assertThat(updated.getValue()).extracting(UserItemCommand::roles).containsExactly(ADMIN_ONLY, USER_ADMIN, USER_ADMIN);
        then(tenantUserQuotaGuard).should().ensureAssignableLocked(TENANT, -2L); // −8 + 6 + 0
    }

    @Test
    @DisplayName("수정 항목 roles 가 빈 목록·SUPER_ADMIN 포함이면 USER_028 — 잠금·교체 전에 거부")
    void update_invalidRoles_rejected() {
        User user = current(20, USER_ONLY, 10L);
        targets(List.of(user));

        assertThat(bulkFails(update(20, 10L, Set.of())).getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        assertThat(bulkFails(update(20, 10L, Set.of(RoleType.USER, RoleType.SUPER_ADMIN))).getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        then(userService).should(never()).lockUsersBySeqList(anyList());
        then(userRoleService).should(never()).replaceRoles(any());
    }

    @Test
    @DisplayName("한 항목의 역할 교체가 거부되면(USER_030) 전체 실패 — 저장 없음")
    void update_replaceRejected_allFail() {
        User first = current(10, USER_ONLY, 10L);
        User second = current(20, USER_ADMIN, 10L, 5L);
        targets(List.of(first, second));
        Mockito.doThrow(ApiRuntimeException.type(UserErrorCode.ROLE_REVOKE_USER_IN_USE))
               .when(userRoleService).replaceRoles(new UserRoleChangeDto(20, ADMIN_ONLY, LOGIN_USER, 5L));

        assertThat(bulkFails(update(10, 10L, USER_ADMIN), update(20, null, ADMIN_ONLY)).getCode())
                .isEqualTo(UserErrorCode.ROLE_REVOKE_USER_IN_USE);
        then(adminUserService).should(never()).upsertAll(anyList(), anyList(), any());
    }

    @Test
    @DisplayName("기존 할당량이 0 인 USER 도 0 그대로면 QUOTA_008 — 용량 판정 전에 거부")
    void update_userKeepZero_rejected() {
        User user = current(20, USER_ONLY, 0L);
        targets(List.of(user));

        assertThat(bulkFails(update(20, 0L)).getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
        then(tenantUserQuotaGuard).should(never()).ensureAssignableLocked(anyInt(), anyLong());
    }

    @Test
    @DisplayName("수정 대상에 SYSTEM_MANAGED 보호 계정이 하나라도 있으면 TENANT_011 — 테넌트 경계 확인 뒤, 조회·스토리지 확인·잠금 전에 전체 거부")
    void update_systemManagedProtected_rejectedFirst() {
        Mockito.doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED))
               .when(tenantPolicy).requireUpdatableUser(20);

        assertThat(bulkFails(update(10, 10L), update(20, 10L)).getCode())
                .isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        then(tenantValidator).should().assertSameTenant(List.of(10, 20));
        then(userFindService).should(never()).findDetailsByUserSeqs(anyList());
        then(quotaService).should(never()).canIncrease(anyLong());
        then(tenantUserQuotaGuard).should(never()).lockTenant(anyInt());
        then(adminUserService).should(never()).upsertAll(anyList(), anyList(), any());
    }

    @Test
    @DisplayName("수정 항목마다 requireUpdatableUser, 등록만 있는 요청은 USER_REGISTER 만 판정")
    void updatableUser_checkedPerUpdateItem() {
        User user = current(20, USER_ONLY, 10L);
        targets(List.of(user));
        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(update(20, 10L))), LOGIN_USER);
        then(tenantPolicy).should().requireUpdatableUser(20);
        then(tenantPolicy).should(never()).require(TENANT, TenantFeature.USER_REGISTER);

        Mockito.clearInvocations(tenantPolicy);
        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(insert("a@test.com", 10L, USER_ONLY))), LOGIN_USER);
        then(tenantPolicy).should().require(TENANT, TenantFeature.USER_REGISTER);
        then(tenantPolicy).should(never()).requireUpdatableUser(anyInt());
    }

    @Test
    @DisplayName("등록 항목이 있고 USER_REGISTER 가 막힌 테넌트(SYSTEM_MANAGED + SINGLE)면 TENANT_011 — 좌석 확인 전에 거부")
    void insert_single_rejectedFirst() {
        Mockito.doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED))
               .when(tenantPolicy).require(TENANT, TenantFeature.USER_REGISTER);

        assertThat(bulkFails(insert("a@test.com", 10L, USER_ONLY)).getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        then(seatLicenseService).should(never()).isSeatAvailable(anyInt(), anyInt());
        then(tenantUserQuotaGuard).should(never()).lockTenant(anyInt());
    }

    @Test
    @DisplayName("보호 계정 역할 변경은 USER_034 — 스토리지 확인·잠금 전에 거부, 같은 역할 재전송은 판정하지 않음")
    void update_protectedRoleChange_rejectedBeforeLock() {
        User user = current(20, USER_ONLY, 10L);
        targets(List.of(user));
        Mockito.doThrow(ApiRuntimeException.type(UserErrorCode.PROTECTED_USER)).when(tenantPolicy).requireManageableUser(20);

        assertThat(bulkFails(update(20, 10L, USER_ADMIN)).getCode()).isEqualTo(UserErrorCode.PROTECTED_USER);
        then(quotaService).should(never()).canIncrease(anyLong());
        then(userService).should(never()).lockUsersBySeqList(anyList());

        adminUserImportUseCase.bulkUpsert(new AdminUserBulkUpsertCommand(List.of(update(20, 10L, USER_ONLY))), LOGIN_USER);
        then(adminUserService).should().upsertAll(anyList(), anyList(), any());
    }
}
