package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.license.service.LicenseService.SeatPolicy;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.admin.dto.AdminUserUpdateDto;
import com.thinkfree.drive.user.admin.exception.exception.AdminUserErrorCode;
import com.thinkfree.drive.user.admin.service.impl.DefaultAdminUserService;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.query.AdminUserBulkUpdateQuery;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.service.UserFindService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * W7 — 단건 변경이 쓰기 리포 {@code findById} 관리 엔티티 + {@link User} 변경 메서드(변경 감지)로 바뀐 Service 경로.
 * 구 JPQL 의 0행 조건(이미 그 상태·대상 상태 아님·다른 테넌트·없음)이 같은 오류 코드로 나오는지, 성공 시 엔티티 필드가 바뀌는지(커밋 때 UPDATE) 본다.
 * DB 반영·감사 컬럼은 {@code DefaultUserRepositoryTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultUserService·DefaultAdminUserService — 단건 변경 = 관리 엔티티 변경(W7)")
class DefaultUserServiceChangeDetectionTest {

    private static final int LOGIN = 2;
    private static final int TARGET = 20;
    private static final int TENANT = 1;

    @Mock private UserRepository userRepository;
    @Mock private UserFindService userFindService;
    @Mock private StorageService storageService;

    @InjectMocks
    private DefaultUserService userService;
    @InjectMocks
    private DefaultAdminUserService adminUserService;

    private User existing(UserStatus status) {
        User u = User.builder().userSeq(TARGET).tenantSeq(TENANT).status(status)
                .userName("이전").totalQuotaByte(10L).useQuotaByte(0L).profileImageKey("profiles/ko/user-" + TARGET + ".png").build();
        given(userRepository.findById(TARGET)).willReturn(u);
        return u;
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Nested
    @DisplayName("상태 전이 — 구 JPQL 0행 조건과 같은 오류")
    class Status {

        @Test
        @DisplayName("activate — 시트 검사 원자 UPDATE 에 테넌트·시트 정책 그대로(엔티티 로드 없음), 0건(없음·이미 ACTIVE·시트 초과)·userSeq null 은 ACTIVATE_FAIL")
        void activate() {
            given(userRepository.activateUser(LOGIN, TARGET, TENANT, true, 5)).willReturn(1);
            userService.activateUser(LOGIN, TARGET, TENANT, true, 5);
            verify(userRepository).activateUser(LOGIN, TARGET, TENANT, true, 5);
            verify(userRepository, never()).findById(anyInt());

            assertCode(() -> userService.activateUser(LOGIN, 999, TENANT, true, 5), UserErrorCode.ACTIVATE_FAIL);
            assertCode(() -> userService.activateUser(LOGIN, null, TENANT, false, 0), UserErrorCode.ACTIVATE_FAIL);
        }

        @Test
        @DisplayName("deactivate — 본인·기본 관리자 거부(로드 전), 이미 INACTIVE·없음은 INACTIVATE_FAIL")
        void deactivate() {
            User u = existing(UserStatus.ACTIVE);
            assertCode(() -> userService.deactivateUser(TARGET, TARGET), UserErrorCode.CANNOT_DEACTIVATE_SELF_OR_DEFAULT_ADMIN);
            verify(userRepository, never()).findById(anyInt());

            userService.deactivateUser(LOGIN, TARGET);
            assertThat(u.getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertCode(() -> userService.deactivateUser(LOGIN, TARGET), UserErrorCode.INACTIVATE_FAIL);
            assertCode(() -> userService.deactivateUser(LOGIN, 999), UserErrorCode.INACTIVATE_FAIL);
        }

        @Test
        @DisplayName("withdraw — ACTIVE·INACTIVE 만 PENDING_DELETE(삭제 예정 시각 기록), 그 밖·없음은 WITHDRAW_PENDING_FAIL")
        void withdraw() {
            User u = existing(UserStatus.INACTIVE);
            Instant now = Instant.parse("2026-10-13T00:00:00Z");
            userService.withdraw(TARGET, now, now.plusSeconds(86400));
            assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING_DELETE);
            assertThat(u.getPendingDeleteDate()).isEqualTo(now);
            assertThat(u.getDeleteScheduleTime()).isEqualTo(now.plusSeconds(86400));

            assertCode(() -> userService.withdraw(TARGET, now, now), UserErrorCode.WITHDRAW_PENDING_FAIL);
            assertCode(() -> userService.withdraw(999, now, now), UserErrorCode.WITHDRAW_PENDING_FAIL);
        }

        @Test
        @DisplayName("restore — PENDING_DELETE 만 INACTIVE(예정 시각 비움), 그 밖·없음은 RESTORE_FAILED")
        void restore() {
            User u = existing(UserStatus.PENDING_DELETE);
            adminUserService.restoreUser(TARGET);
            assertThat(u.getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertThat(u.getPendingDeleteDate()).isNull();
            assertThat(u.getDeleteScheduleTime()).isNull();

            assertCode(() -> adminUserService.restoreUser(TARGET), AdminUserErrorCode.RESTORE_FAILED);
            assertCode(() -> adminUserService.restoreUser(999), AdminUserErrorCode.RESTORE_FAILED);
        }
    }

    @Test
    @DisplayName("cancelMfa — ENABLED·같은 테넌트만 DISABLED(방법·비밀키·시각 비움), 다른 테넌트·이미 해제·없음은 MFA_CANCEL_FAIL")
    void cancelMfa() {
        User u = User.builder().userSeq(TARGET).tenantSeq(TENANT).status(UserStatus.ACTIVE)
                .mfaStatus(MfaStatus.ENABLED).mfaMethod(MfaMethod.TOTP).mfaSecretKey("s").mfaSetupDate(Instant.now()).mfaConfirmDate(Instant.now()).build();
        given(userRepository.findById(TARGET)).willReturn(u);

        assertCode(() -> userService.cancelMfa(TARGET, 999), UserErrorCode.MFA_CANCEL_FAIL);
        assertThat(u.getMfaStatus()).as("다른 테넌트 — 변경 없음").isEqualTo(MfaStatus.ENABLED);

        userService.cancelMfa(TARGET, TENANT);
        assertThat(u.getMfaStatus()).isEqualTo(MfaStatus.DISABLED);
        assertThat(u.getMfaMethod()).isNull();
        assertThat(u.getMfaSecretKey()).isNull();
        assertThat(u.getMfaSetupDate()).isNull();
        assertThat(u.getMfaConfirmDate()).isNull();

        assertCode(() -> userService.cancelMfa(TARGET, TENANT), UserErrorCode.MFA_CANCEL_FAIL);
        assertCode(() -> userService.cancelMfa(999, TENANT), UserErrorCode.MFA_CANCEL_FAIL);
    }

    @Test
    @DisplayName("updateUserByAdmin — 현재 프로필 키는 로드한 관리 엔티티 값(판정용 DTO 재조회 없음 — 이중 로드 통합), 변경은 changeByAdmin, 없으면 USER_NOT_FOUND")
    void updateUserByAdmin_singleLoad() {
        User u = existing(UserStatus.ACTIVE);
        userService.updateUserByAdmin(LOGIN, TARGET, new AdminUserUpdateDto("새이름", 500L, "KR", "010", null, false));

        verifyNoInteractions(userFindService);
        assertThat(u.getUserName()).isEqualTo("새이름");
        assertThat(u.getTotalQuotaByte()).isEqualTo(500L);
        assertThat(u.getCountryCode()).isEqualTo("KR");
        assertThat(u.getPhoneNumber()).isEqualTo("010");
        assertThat(u.getProfileImageKey()).as("파일·삭제 요청 없음 — 엔티티의 현재 키 유지").isEqualTo("profiles/ko/user-" + TARGET + ".png");


        assertCode(() -> userService.updateUserByAdmin(LOGIN, 999, new AdminUserUpdateDto("x", 1L, null, null, null, false)), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("upsertAll update 행 — 리포 배치 UPDATE 에 Dto 값 그대로(B1), 엔티티 로드·변경 없음, 빈 updates 면 호출 없음")
    void upsertAll_delegatesBatchUpdate() {
        SeatPolicy seatPolicy = new SeatPolicy(false, 0);

        adminUserService.upsertAll(List.of(), List.of(
                new AdminUserBulkUpdateDto(TARGET, "일괄", 77L, "US", "111"),
                new AdminUserBulkUpdateDto(TARGET + 1, "둘", 5L, null, null)), seatPolicy);
        verify(userRepository).updateAll(List.of(
                new AdminUserBulkUpdateQuery(TARGET, "일괄", 77L, "US", "111"),
                new AdminUserBulkUpdateQuery(TARGET + 1, "둘", 5L, null, null)));
        verify(userRepository, never()).findById(anyInt());

        clearInvocations(userRepository);
        adminUserService.upsertAll(List.of(), List.of(), seatPolicy);
        verify(userRepository, never()).updateAll(anyList());
    }
}
