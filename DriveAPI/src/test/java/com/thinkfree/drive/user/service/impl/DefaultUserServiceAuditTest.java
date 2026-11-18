package com.thinkfree.drive.user.service.impl;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.user.dto.UserUpdateDto;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.converter.UserConverter;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserIdentity;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.token.entity.PasswordResetToken;
import com.thinkfree.drive.user.token.repository.PasswordResetTokenRepository;
import com.thinkfree.drive.userlog.service.UserLogService;
import jakarta.servlet.http.HttpServletRequest;
import com.thinkfree.drive.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 사용자 "본인"이 직접 수행하는 정보/비밀번호 변경 경로가 <b>UserAuditService를 어떤 인자로 호출하는지</b>를 검증한다.
 * (관리자 경로는 AdminUseCaseAuditTest에서 별도 검증 — 여기선 UserController → UserService 직결 경로만 다룸)
 * 기록 내용은 {@code DefaultUserAuditServiceTest}에서 검증한다.
 */
@DisplayName("DefaultUserService(본인 정보/비밀번호 변경) 감사 로그 기록 흐름 테스트")
class DefaultUserServiceAuditTest extends AuditMockTest {

    private static final int TENANT = 1;

    @Mock private UserRepository userRepository;
    @Mock private UserQueryRepository userQueryRepository;
    @Mock private UserFindService userFindService;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private StorageService storageService;
    @Mock private UserConverter userConverter;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private UserLogService userLogService;
    @Mock private LicenseService licenseService;
    @Mock private BrandingMetaService brandingMetaService;
    @Mock private UserAuditService userAuditService;
    @Mock private UserRoleFindService userRoleFindService;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private DefaultUserService userService;

    /** 쓰기 리포 findById = 고칠 관리 엔티티(W7 — 비밀번호·본인 수정은 변경 감지) */
    @BeforeEach
    void stubManagedUser() {
        lenient().when(userRepository.findById(anyInt())).thenAnswer(inv -> User.builder().userSeq(inv.getArgument(0)).build());
    }

    private <T extends UserIdentity> T userMock(Class<T> type, int seq, RoleType role) {
        T u = Mockito.mock(type);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        // ADMIN_/USER_ 분기 = 대상의 ADMIN 역할 보유(tb_user_role)
        Mockito.lenient().when(userRoleFindService.holds(seq, RoleType.ADMIN)).thenReturn(role == RoleType.ADMIN);
        return u;
    }

    // EventName: USER_INFO_UPDATE (본인 - 전화번호/국가코드만 대상)
    @Test
    @DisplayName("updateCurrentUser - 변경 전 유저 + 요청의 국가코드/전화번호로 recordSelfInfoUpdate를 호출한다")
    void updateCurrentUser_recordsInfoUpdate() {
        UserDetailData before = userMock(UserDetailData.class, 20, RoleType.USER);
        given(userFindService.findDetailByUserSeq(20)).willReturn(before);

        var request = new UserUpdateDto("010-2222-2222", null, null, null, "+1", false);

        userService.updateCurrentUser(20, request);

        verify(userAuditService).recordSelfInfoUpdate(before, "+1", "010-2222-2222");
    }

    // EventName: USER_PASSWORD_CHANGE / ADMIN_PASSWORD_CHANGE (대상 계정 role 기준)
    @Test
    @DisplayName("updatePassword - 대상 계정 role에 따라 record(USER_PASSWORD_CHANGE/ADMIN_PASSWORD_CHANGE, user)를 호출한다")
    void updatePassword_recordsPasswordChangeByTargetRole() {
        UserAuthData user = userMock(UserAuthData.class, 20, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(user.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(eq("curPw"), any())).willReturn(true);  // 현재 비번 일치
        given(pwd.isSamePassword(eq("NewPass1!"), any())).willReturn(false); // 새 비번은 다름
        given(userFindService.findAuthByUserSeq(20)).willReturn(user);

        userService.updatePassword(20, "user20@thinkfree.com", "curPw", "NewPass1!", Mockito.mock(HttpServletRequest.class));

        verify(userAuditService).record(EventName.USER_PASSWORD_CHANGE, user);

        UserAuthData admin = userMock(UserAuthData.class, 21, RoleType.ADMIN);
        Password adminPwd = Mockito.mock(Password.class);
        given(admin.getEncPwd()).willReturn(adminPwd);
        given(adminPwd.isSamePassword(eq("curPw"), any())).willReturn(true);
        given(adminPwd.isSamePassword(eq("NewPass1!"), any())).willReturn(false);
        given(userFindService.findAuthByUserSeq(21)).willReturn(admin);

        userService.updatePassword(21, "user21@thinkfree.com", "curPw", "NewPass1!", Mockito.mock(HttpServletRequest.class));
        verify(userAuditService).record(EventName.ADMIN_PASSWORD_CHANGE, admin);
    }

    // EventName: USER_PASSWORD_CHANGE (재설정 링크 - 미인증이라 행위자=대상 본인으로 override)
    @Test
    @DisplayName("resetPasswordByToken - 대상 유저로 recordSelfActed(USER_PASSWORD_CHANGE)를 호출한다")
    void resetPasswordByToken_recordsPasswordChangeWithSelfActor() {
        TenantContext.set(TENANT);

        UserDetailData user = userMock(UserDetailData.class, 20, RoleType.USER);
        given(user.getTenantSeq()).willReturn(TENANT);

        PasswordResetToken resetToken = Mockito.mock(PasswordResetToken.class);
        given(resetToken.getUserSeq()).willReturn(20);
        given(resetToken.getTokenHash()).willReturn("hash");
        given(resetToken.getExpirationDate()).willReturn(Instant.now().plusSeconds(3600));

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(resetToken);
        given(userFindService.findDetailByUserSeq(20)).willReturn(user);
        given(passwordResetTokenRepository.deleteByTokenHash("hash")).willReturn(1);

        userService.resetPasswordByToken("raw-token", "NewPass1!", Mockito.mock(HttpServletRequest.class));

        verify(userAuditService).recordSelfActed(EventName.USER_PASSWORD_CHANGE, user);
    }

    // EventName: ADMIN_PASSWORD_CHANGE (재설정 링크, 관리자 계정)
    @Test
    @DisplayName("resetPasswordByToken - 관리자 계정이면 recordSelfActed(ADMIN_PASSWORD_CHANGE)를 호출한다")
    void resetPasswordByToken_adminRole() {
        TenantContext.set(TENANT);

        UserDetailData admin = userMock(UserDetailData.class, 21, RoleType.ADMIN);
        given(admin.getTenantSeq()).willReturn(TENANT);

        PasswordResetToken resetToken = Mockito.mock(PasswordResetToken.class);
        given(resetToken.getUserSeq()).willReturn(21);
        given(resetToken.getTokenHash()).willReturn("hash");
        given(resetToken.getExpirationDate()).willReturn(Instant.now().plusSeconds(3600));

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(resetToken);
        given(userFindService.findDetailByUserSeq(21)).willReturn(admin);
        given(passwordResetTokenRepository.deleteByTokenHash("hash")).willReturn(1);

        userService.resetPasswordByToken("raw-token", "NewPass1!", Mockito.mock(HttpServletRequest.class));

        verify(userAuditService).recordSelfActed(EventName.ADMIN_PASSWORD_CHANGE, admin);
    }

    /** 5차 패스 A11 — 조회는 통과했지만 삭제가 0행(동시 요청이 먼저 소비)이면 거부, 비밀번호·감사 기록 없음 */
    @Test
    @DisplayName("resetPasswordByToken - 토큰 삭제 0행(이미 소비)이면 INVALID_PASSWORD_RESET_TOKEN, 비밀번호 변경 없음")
    void resetPasswordByToken_alreadyConsumed() {
        TenantContext.set(TENANT);

        UserDetailData user = userMock(UserDetailData.class, 20, RoleType.USER);
        given(user.getTenantSeq()).willReturn(TENANT);

        PasswordResetToken resetToken = Mockito.mock(PasswordResetToken.class);
        given(resetToken.getTokenHash()).willReturn("hash");
        given(resetToken.getExpirationDate()).willReturn(Instant.now().plusSeconds(3600));
        given(resetToken.getUserSeq()).willReturn(20);

        given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(resetToken);
        given(userFindService.findDetailByUserSeq(20)).willReturn(user);
        given(passwordResetTokenRepository.deleteByTokenHash("hash")).willReturn(0);

        assertThatThrownBy(() -> userService.resetPasswordByToken("raw-token", "NewPass1!", Mockito.mock(HttpServletRequest.class)))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UserErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        verify(userRepository, never()).findById(anyInt());
        verify(userAuditService, never()).recordSelfActed(any(), any());
    }
}
