package com.thinkfree.drive.user.role.service.impl;

import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.entity.UserRole;
import com.thinkfree.drive.user.role.repository.UserRoleQueryRepository;
import com.thinkfree.drive.user.role.repository.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 역할 부여 공통 메서드 — 계정 생성 시점 불변식(역할 0개 금지·부여 가능 = USER·ADMIN, 단독 역할 불가)과 부여 행(등록자·역할마다 1행),
 * 기존 계정 역할 교체(차이만 부여·회수, 본인 ADMIN 회수 금지, 사용량 있는 USER 회수 금지).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultUserRoleService — 역할 부여·교체 불변식")
class DefaultUserRoleServiceTest {

    private static final int REGISTER_USER = 2;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRoleQueryRepository userRoleQueryRepository;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private DefaultUserRoleService service;

    @SuppressWarnings("unchecked")
    private List<UserRole> grantedRows() {
        ArgumentCaptor<List<UserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).grantAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("단건 — 역할마다 1행(역할 enum 순서), 등록자는 넘긴 값")
    void assignSingleAccount() {
        service.assignInitialRoles(new UserRoleAssignmentDto(10, Set.of(RoleType.USER, RoleType.ADMIN), REGISTER_USER));

        assertThat(grantedRows())
                .extracting(UserRole::getUserSeq, UserRole::getRoleType, UserRole::getRegisterUserSeq)
                .containsExactly(tuple(10, RoleType.ADMIN, REGISTER_USER), tuple(10, RoleType.USER, REGISTER_USER));
    }

    @ParameterizedTest
    @EnumSource(value = RoleType.class, names = {"SUPER_ADMIN", "ANONYMOUS"})
    @DisplayName("단독 역할(SUPER_ADMIN·ANONYMOUS)은 API 로 부여 불가 — 단독이든 섞이든 USER_013, 쓰지 않음")
    void exclusiveRolesRejected(RoleType exclusive) {
        for (Set<RoleType> roles : List.of(Set.of(exclusive), Set.of(exclusive, RoleType.USER))) {
            var e = catchThrowableOfType(ApiRuntimeException.class,
                    () -> service.assignInitialRoles(new UserRoleAssignmentDto(10, roles, REGISTER_USER)));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.INSERT_FAIL);
        }
        verify(userRoleRepository, never()).grantAll(anyList());
    }

    @Test
    @DisplayName("역할 0개·역할 없음(null)·대상 없음 — USER_013, 쓰지 않음")
    void emptyRolesRejected() {
        for (UserRoleAssignmentDto dto : List.of(
                new UserRoleAssignmentDto(10, Set.of(), REGISTER_USER),
                new UserRoleAssignmentDto(10, null, REGISTER_USER),
                UserRoleAssignmentDto.single(10, null, REGISTER_USER),
                UserRoleAssignmentDto.single(null, RoleType.USER, REGISTER_USER))) {
            var e = catchThrowableOfType(ApiRuntimeException.class, () -> service.assignInitialRoles(dto));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.INSERT_FAIL);
        }
        verify(userRoleRepository, never()).grantAll(any());
    }

    @Test
    @DisplayName("여러 계정 — 전부 검사한 뒤 한 번에 부여, 하나라도 위반이면 아무것도 쓰지 않음, 빈/널 목록 no-op")
    void assignAll() {
        service.assignInitialRolesAll(List.of(
                UserRoleAssignmentDto.single(10, RoleType.USER, REGISTER_USER),
                UserRoleAssignmentDto.single(11, RoleType.ADMIN, REGISTER_USER)));
        assertThat(grantedRows())
                .extracting(UserRole::getUserSeq, UserRole::getRoleType)
                .containsExactly(tuple(10, RoleType.USER), tuple(11, RoleType.ADMIN));
    }

    @Test
    @DisplayName("여러 계정 중 하나라도 위반 — USER_013, 앞 계정도 쓰지 않음 / 빈·널 목록은 저장소 호출 없음")
    void assignAllRejectsBeforeWriting() {
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> service.assignInitialRolesAll(List.of(
                UserRoleAssignmentDto.single(10, RoleType.USER, REGISTER_USER),
                UserRoleAssignmentDto.single(11, RoleType.SUPER_ADMIN, REGISTER_USER))));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.INSERT_FAIL);

        service.assignInitialRolesAll(List.of());
        service.assignInitialRolesAll(null);
        verify(userRoleRepository, never()).grantAll(any());
    }

    // ------------------------------------------------------------------ 역할 교체

    private static final int TARGET = 10;
    private static final int ACTOR = 2;

    private void holds(RoleType... roles) {
        given(userRoleQueryRepository.findRoles(TARGET)).willReturn(Set.of(roles));
    }

    @Test
    @DisplayName("교체 — 차이만: 새 역할은 부여(등록자 = 요청자), 빠진 역할은 회수, 그대로인 역할은 손대지 않음")
    void replace_grantsAndRevokesDiffOnly() {
        holds(RoleType.USER);
        service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER, RoleType.ADMIN), ACTOR, 100L));

        assertThat(grantedRows())
                .extracting(UserRole::getUserSeq, UserRole::getRoleType, UserRole::getRegisterUserSeq)
                .containsExactly(tuple(TARGET, RoleType.ADMIN, ACTOR));
        verify(userRoleRepository, never()).revoke(anyInt(), any());
    }

    @Test
    @DisplayName("교체 — 다른 계정의 ADMIN 회수·사용량 0 인 USER 회수는 허용")
    void replace_revokeAllowed() {
        holds(RoleType.USER, RoleType.ADMIN);
        service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER), ACTOR, 100L));
        verify(userRoleRepository).revoke(TARGET, RoleType.ADMIN);

        holds(RoleType.USER, RoleType.ADMIN);
        service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.ADMIN), ACTOR, 0L));
        verify(userRoleRepository).revoke(TARGET, RoleType.USER);
    }

    @Test
    @DisplayName("교체 — 본인 ADMIN 회수는 USER_031, 쓰지 않음")
    void replace_selfAdminRevoke_rejected() {
        holds(RoleType.USER, RoleType.ADMIN);

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER), TARGET, 0L)));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.ROLE_REVOKE_SELF_ADMIN);
        verify(userRoleRepository, never()).revoke(anyInt(), any());
        verify(userRoleRepository, never()).grantAll(anyList());
    }

    @Test
    @DisplayName("교체 — 본인이어도 ADMIN 을 유지하는 교체(USER 추가 등)는 허용")
    void replace_selfKeepsAdmin_allowed() {
        holds(RoleType.ADMIN);
        service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER, RoleType.ADMIN), TARGET, 0L));

        assertThat(grantedRows()).extracting(UserRole::getRoleType).containsExactly(RoleType.USER);
    }

    @Test
    @DisplayName("교체 — 사용량 > 0 인 계정의 USER 회수는 USER_030, 쓰지 않음")
    void replace_userRevokeInUse_rejected() {
        holds(RoleType.USER, RoleType.ADMIN);

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.ADMIN), ACTOR, 1L)));
        assertThat(e.getCode()).isEqualTo(UserErrorCode.ROLE_REVOKE_USER_IN_USE);
        verify(userRoleRepository, never()).revoke(anyInt(), any());
        verify(userRoleRepository, never()).grantAll(anyList());
    }

    @Test
    @DisplayName("교체 — 요청 역할 0개·단독 역할 포함은 USER_028, 대상이 단독 역할 보유·역할 없음이면 USER_005")
    void replace_invalid_rejected() {
        for (Set<RoleType> roles : List.of(Set.<RoleType>of(), Set.of(RoleType.SUPER_ADMIN), Set.of(RoleType.USER, RoleType.ANONYMOUS))) {
            var e = catchThrowableOfType(ApiRuntimeException.class,
                    () -> service.replaceRoles(new UserRoleChangeDto(TARGET, roles, ACTOR, 0L)));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.INVALID_ROLE_PARAM);
        }
        for (Set<RoleType> held : List.of(Set.of(RoleType.SUPER_ADMIN), Set.of(RoleType.ANONYMOUS), Set.<RoleType>of())) {
            given(userRoleQueryRepository.findRoles(TARGET)).willReturn(held);
            var e = catchThrowableOfType(ApiRuntimeException.class,
                    () -> service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER), ACTOR, 0L)));
            assertThat(e.getCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
        verify(userRoleRepository, never()).revoke(anyInt(), any());
        verify(userRoleRepository, never()).grantAll(anyList());
    }

    @Test
    @DisplayName("교체 — 보호 사용자(유형 무관)는 역할이 바뀌면 USER_PROTECTED, 쓰지 않음")
    void replace_protectedUser_rejected() {
        holds(RoleType.USER, RoleType.ADMIN);
        org.mockito.Mockito.doThrow(ApiRuntimeException.type(UserErrorCode.PROTECTED_USER))
                .when(tenantPolicy).requireManageableUser(TARGET);

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.ADMIN), ACTOR, 0L)));

        assertThat(e.getCode()).isEqualTo(UserErrorCode.PROTECTED_USER);
        verify(userRoleRepository, never()).revoke(anyInt(), any());
        verify(userRoleRepository, never()).grantAll(anyList());
    }

    @Test
    @DisplayName("교체 — 같은 역할 그대로면 보호 판정 없이 통과(bulk 수정이 역할을 그대로 보내는 경우)")
    void replace_sameRoles_skipsProtectionCheck() {
        holds(RoleType.USER, RoleType.ADMIN);

        service.replaceRoles(new UserRoleChangeDto(TARGET, Set.of(RoleType.USER, RoleType.ADMIN), ACTOR, 0L));

        verify(tenantPolicy, never()).requireManageableUser(anyInt());
    }
}
