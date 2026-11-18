package com.thinkfree.drive.application.admin.user;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.command.AdminUpdateUserCommand;
import com.thinkfree.drive.application.admin.converter.AdminCommandConverter;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.quota.policy.TenantQuotaPolicy;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.role.dto.UserRoleChangeDto;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.util.DateUtil;
import com.thinkfree.drive.valid.DriveAssert;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * 진입점(내부 API·MCP) 공용 관리자 계정 관리 usecase — 단건 변경(등록·수정·비밀번호·활성/비활성·탈퇴·MFA 초기화·즉시 삭제).
 * 조회는 {@link AdminUserQueryUseCase}, 일괄 등록/수정·CSV 가져오기는 {@link AdminUserImportUseCase}.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 접근 제어는 어댑터 책임 — 웹은 시큐리티 URL role 게이트(/api/v1/admins/** = ADMIN),
 * MCP는 툴 클래스의 @McpToolRole(ADMIN) 어스펙트가 담당한다.
 * 테넌트 격리는 TenantContext 기반(웹·MCP 모두 TenantFilter가 Host 기준으로 주입).
 * 웹 컨트롤러의 @TenantUser(경로 userSeq 테넌트 검증)는 MCP 경로에 없으므로, userSeq를 받는 메서드를 툴에서
 * 쓸 때는 툴 파라미터에 @McpToolTenantUser를 붙여 동일 검증을 수행해야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserUseCase {

    private final AdminCommandConverter adminCommandConverter;
    private final UserService userService;
    private final UserFindService userFindService;
    private final LicenseService licenseService;
    private final SeatLicenseService seatLicenseService;
    private final QuotaService quotaService;
    private final TenantUserQuotaGuard tenantUserQuotaGuard;
    private final MfaService mfaService;
    private final UserPermanentDeletionService userPermanentDeletionService;
    private final UserAuditLookupService userAuditLookupService;
    private final UserAuditService userAuditService;
    private final LoginAuditService loginAuditService;
    private final UserRoleService userRoleService;
    private final UserRoleFindService userRoleFindService;
    /** SYSTEM_MANAGED 보호 사용자 — 탈퇴·삭제·비활성 거부(역할·비밀번호는 UserRoleService·UserService 가 판정) */
    private final TenantPolicy tenantPolicy;

    @AuditLog
    @Transactional // 테넌트 총 용량 판정의 테넌트 행 잠금을 사용자 INSERT 커밋까지 유지
    @TenantFeatureRequired(TenantFeature.USER_REGISTER) // SYSTEM_MANAGED + SINGLE 은 계정 하나(테넌트 생성 때 만든 보호 계정)만 — 좌석·용량 판정 전에 거부
    public int registerUser(AdminRegisterUserCommand command, int loginUserSeq){
        // role 상한(채널 무관): USER/ADMIN 1개 이상 — roles 는 enum 바인딩이라 SUPER_ADMIN·ANONYMOUS 도 커맨드까지 도달할 수 있다
        DriveAssert.throwIfFalse(RoleType.isAssignable(command.roles()), UserErrorCode.INVALID_ROLE_PARAM);

        // 보유 역할별 할당량 확정 — USER 보유면 필수·0 보다 커야 하고, ADMIN 만이면 0(생략 시 0)
        command = command.withTotalQuotaByte(TenantQuotaPolicy.resolveUserQuota(command.roles(), command.totalQuotaByte()));

        int tenantSeq = TenantContext.getOrThrow();
        if (!seatLicenseService.isSeatAvailable(tenantSeq, 1)) {
            throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
        }

        command.validatePhoneFields();
        quotaService.ensureCanAssignQuotaIncrease(command.totalQuotaByte());
        tenantUserQuotaGuard.ensureAssignable(tenantSeq, command.totalQuotaByte());
        var saveDto = adminCommandConverter.toSaveDto(command, loginUserSeq, tenantSeq);
        var result = userService.registerUser(saveDto, command.profileImageFile());

        EventName eventName = command.roles().contains(RoleType.ADMIN) ? EventName.ADMIN_CREATE : EventName.USER_CREATE;
        userAuditService.record(eventName, (long) result.userSeq(), command.userId(), command.userName());

        return result.userSeq();
    }

    @AuditLog
    @Transactional
    public void updateUserByAdmin(int loginUserSeq, Integer userSeq, AdminUpdateUserCommand command) {
        // SYSTEM_MANAGED 보호 계정은 계정 값을 외부 시스템이 정한다 — 조회·스토리지 확인·잠금 전에 거부
        tenantPolicy.requireUpdatableUser(userSeq);

        command.validatePhoneFields();
        // roles 생략 = 역할 유지. 지정하면 USER/ADMIN 1개 이상
        boolean changesRoles = command.roles() != null;
        DriveAssert.throwIfTrue(changesRoles && !RoleType.isAssignable(command.roles()), UserErrorCode.INVALID_ROLE_PARAM);

        // 스토리지(Ceph) 가용량은 잠금 전에 — 외부 호출이 느려도 테넌트 잠금을 쥐지 않게 한다.
        // Ceph 공간은 테넌트끼리 공유·예약 없음이라 잠금 안에서 확인해도 정확도가 같다(근사 검사). 판정은 잠금 없이 읽은 현재 값 기준.
        var current = userFindService.findDetailByUserSeq(userSeq);
        Set<RoleType> currentRoles = userRoleFindService.findRoles(userSeq);
        validateUpdatableTarget(currentRoles);
        // 보호 계정 역할 변경은 스토리지 확인·잠금 전에 거부(같은 역할 재전송은 변경 아님) — 잠금 뒤 replaceRoles 가 같은 규칙으로 최종 판정
        if (changesRoles && !command.roles().equals(currentRoles)) {
            tenantPolicy.requireManageableUser(userSeq);
        }
        long requestedQuotaByte = TenantQuotaPolicy.resolveUserQuota(changesRoles ? command.roles() : currentRoles, command.totalQuotaByte());
        quotaService.ensureCanUpdateQuota(requestedQuotaByte, current.getTotalQuotaByte(), current.getUseQuotaByte());

        // 잠금 순서: 테넌트 → 사용자(PERSEAT 활성화·일괄 등록과 같은 순서 — 교착 방지)
        int tenantSeq = TenantContext.getOrThrow();
        tenantUserQuotaGuard.lockTenant(tenantSeq);
        var user = userService.lockUserBySeq(userSeq);

        // 잠근 값으로 다시 판정 — 보유 역할(USER/ADMIN), 역할 교체 규칙, 역할별 할당량, 사용량 미만 금지, 테넌트 총 용량
        // 역할 변경은 이 사용자 행 잠금으로 직렬화된다(역할을 바꾸는 경로는 모두 이 잠금 뒤)
        currentRoles = userRoleFindService.findRoles(userSeq);
        validateUpdatableTarget(currentRoles);
        Set<RoleType> resultRoles = changesRoles ? command.roles() : currentRoles;
        if (changesRoles) {
            // 본인 ADMIN 회수·사용량 있는 USER 회수 거부 — 용량 판정보다 먼저(회수 거부 사유가 용량 오류로 가려지지 않게)
            userRoleService.replaceRoles(new UserRoleChangeDto(userSeq, resultRoles, loginUserSeq, user.getUseQuotaByte()));
        }
        command = command.withTotalQuotaByte(TenantQuotaPolicy.resolveUserQuota(resultRoles, command.totalQuotaByte()));

        if(user.isInvalidTotalQuotaChange(command.totalQuotaByte())){
            throw new ApiRuntimeException(UserErrorCode.QUOTA_DECREASE_NOT_ALLOWED);
        }

        tenantUserQuotaGuard.ensureAssignableLocked(tenantSeq, command.totalQuotaByte() - user.getTotalQuotaByte());

        // lockUserBySeq 는 관리 엔티티(잠금) — 아래 updateUserByAdmin 이 같은 인스턴스를 고친다(1차 캐시).
        // 감사 로그 "변경 전" 스냅샷(DTO)은 update 실행 전에 별도로 조회한다.
        UserDetailData beforeUser = userAuditLookupService.findUser(userSeq);

        userService.updateUserByAdmin(loginUserSeq, userSeq, adminCommandConverter.toUpdateDto(command));

        userAuditService.recordInfoUpdate(beforeUser, command.userName(), command.totalQuotaByte(), command.countryCode(), command.phoneNumber());
    }

    @AuditLog
    @Transactional
    public void updatePasswordByAdmin(Integer userSeq, String newPwd, HttpServletRequest httpRequest) {
        UserAuthData user = userService.updatePasswordByAdmin(userSeq, newPwd, httpRequest);

        // 5회 이상 로그인 실패로 잠긴 계정만 "잠금 해제"다 — 잠기지 않은 계정은 상태 전환이 없으므로 기록하지 않는다.
        if (user.getLoginFailureCount() >= 5) {
            loginAuditService.recordAccountUnlocked(user);
        }

        EventName passwordChangeEventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN)
                ? EventName.ADMIN_PASSWORD_CHANGE
                : EventName.USER_PASSWORD_CHANGE;
        userAuditService.record(passwordChangeEventName, user);
    }

    @AuditLog
    @Transactional
    @TenantFeatureRequired(TenantFeature.SUB_ACCOUNT_MANAGE)
    public void activateUser(Integer loginUserSeq, Integer userSeq) {
        UserDetailData user = userAuditLookupService.findUser(userSeq);

        int tenantSeq = TenantContext.getOrThrow();
        if (!seatLicenseService.isSeatAvailable(tenantSeq, 1)) {
            throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
        }

        var seatPolicy = licenseService.getSeatPolicy(tenantSeq);
        userService.activateUser(loginUserSeq, userSeq, tenantSeq, seatPolicy.isSeat(), seatPolicy.constrainValue());

        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_STATUS_CHANGE : EventName.USER_STATUS_CHANGE;
        userAuditService.record(eventName, user, user.getStatus().name(), UserStatus.ACTIVE.name());
    }

    @AuditLog
    @Transactional
    public void deactivateUser(Integer loginUserSeq, Integer userSeq) {
        tenantPolicy.requireManageableUser(userSeq);
        UserDetailData user = userAuditLookupService.findUser(userSeq);

        userService.deactivateUser(loginUserSeq, userSeq);

        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_STATUS_CHANGE : EventName.USER_STATUS_CHANGE;
        userAuditService.record(eventName, user, user.getStatus().name(), UserStatus.INACTIVE.name());
    }

    @AuditLog
    public void withdraw(int loginUserSeq, int userSeq){

        if(loginUserSeq == userSeq || userSeq == DriveConstants.SUPER_ADMIN_USER_SEQ) {
            throw ApiRuntimeException.type(UserErrorCode.CANNOT_DELETE_SELF_OR_DEFAULT_ADMIN);
        }
        tenantPolicy.requireManageableUser(userSeq);

        var user = userAuditLookupService.findUser(userSeq);

        var now = Instant.now();
        var deleteScheduleTime = DateUtil.calcStartDay(now, DriveConstants.USER_PENDING_RETENTION);
        userService.withdraw(userSeq, now, deleteScheduleTime);

        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_DELETE : EventName.USER_DELETE;
        userAuditService.record(eventName, user);
    }

    @AuditLog
    @TenantFeatureRequired(TenantFeature.MFA_SETTING)
    public void resetMfa(int userSeq) {
        var user = userAuditLookupService.findUser(userSeq);

        mfaService.cancel(userSeq, TenantContext.getOrThrow());

        userAuditService.record(EventName.USER_TWOFACTOR_RESET, user);
    }

    @AuditLog
    public void deleteUser(int userSeq, int actorUserSeq) {
        tenantPolicy.requireManageableUser(userSeq);
        userPermanentDeletionService.deleteUserDirectly(userSeq, actorUserSeq);
    }

    // 수정 대상은 보유 역할이 전부 등록 가능한 역할(USER/ADMIN)인 계정만 — SUPER_ADMIN·ANONYMOUS 는 설치 시 만들어지는 단독 역할 계정이라 관리자 수정 대상이 아니다
    private static void validateUpdatableTarget(Set<RoleType> roles) {
        DriveAssert.throwIfFalse(RoleType.isAssignable(roles), UserErrorCode.USER_NOT_FOUND);
    }

}
