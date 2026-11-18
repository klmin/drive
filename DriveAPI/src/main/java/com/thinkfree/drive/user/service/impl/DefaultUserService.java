package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.common.validation.EmailValidator;
import com.thinkfree.drive.common.validation.ProfileImageValidator;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.event.user.UserPasswordResetRequestedEvent;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.dto.AdminUserUpdateDto;
import com.thinkfree.drive.user.converter.UserConverter;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.dto.*;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.ProfileType;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.user.token.entity.PasswordResetToken;
import com.thinkfree.drive.user.token.repository.PasswordResetTokenRepository;
import com.thinkfree.drive.userlog.enums.ActorType;
import com.thinkfree.drive.userlog.service.UserLogService;
import com.thinkfree.drive.util.Base62TokenGenerator;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.valid.DriveAssert;
import com.thinkfree.drive.weboffice.support.CursorColors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserService implements UserService {

    private static final String[] ANONYMOUS_ANIMAL_FILE_NAMES = {
            "panda", "capybara", "rhino", "kiwi", "rabbit",
            "leopard", "hippo", "hamster", "giraffe", "camel",
            "squirrel", "dolphin", "dinosaur", "crow", "otter",
            "crocodile", "fox", "monkey", "badger", "wombat",
            "unicorn", "narwhal", "jackal", "shrew", "deer",
            "buffalo", "manatee", "blobfish", "skunk", "armadillo"
    };

    private final UserRepository userRepository;
    private final UserQueryRepository userQueryRepository;
    private final UserFindService userFindService;
    private final StorageService storageService;
    private final UserConverter userConverter;
    private final ApplicationEventPublisher publisher;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserLogService userLogService;
    private final LicenseService licenseService;
    private final BrandingMetaService brandingMetaService;
    private final UserAuditService userAuditService;
    private final UserRoleService userRoleService;
    private final UserRoleFindService userRoleFindService;
    /** SYSTEM_MANAGED — SINGLE 등록 거부, 보호 사용자 비밀번호 변경·재설정 거부 */
    private final TenantPolicy tenantPolicy;

    @Value("${auth.token.password-reset-expiry}")
    private Duration passwordResetExpiry;

    @Transactional
    public UserSaveResult registerUser(AdminUserSaveDto saveDto, MultipartFile profileImageFile) {
        // SYSTEM_MANAGED + SINGLE 등록 거부(USER_REGISTER)는 진입점 AdminUserUseCase.registerUser 가 좌석·용량 판정 전에 한다
        return register(saveDto, profileImageFile, false);
    }

    @Transactional
    public UserSaveResult registerTenantAdmin(AdminUserSaveDto saveDto) {
        return register(saveDto, null, true);
    }

    private UserSaveResult register(AdminUserSaveDto saveDto, MultipartFile profileImageFile, boolean protectedUser) {
        log.info("{ UserService } : 사용자 등록 ");

        EmailValidator.validateOrThrow(saveDto.userId());
        validateDuplicate(saveDto.tenantSeq(), saveDto.userId());

        User user = User.createUser(saveDto.userId(), saveDto.tenantSeq(), Password.encrypt(saveDto.encPwd(), ENCODER), SignupType.LOCAL, saveDto.userName(), UserStatus.ACTIVE,
                saveDto.totalQuotaByte(), saveDto.registerUserSeq(), null, saveDto.countryCode(), saveDto.phoneNumber());
        if (protectedUser) {
            user.markProtected();
        }

        var seatPolicy = licenseService.getSeatPolicy(saveDto.tenantSeq());
        boolean inserted = userRepository.insertUserWithSeatCheck(user, seatPolicy.isSeat(), seatPolicy.constrainValue());
        log.info("[사용자 등록] userId={}, isSeat={}, constrainValue={}, inserted={}", user.getUserId(), seatPolicy.isSeat(), seatPolicy.constrainValue(), inserted);
        if (!inserted) {
            throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
        }
        userRoleService.assignInitialRoles(new UserRoleAssignmentDto(user.getUserSeq(), saveDto.roles(), user.getRegisterUserSeq()));

        // 프로필 이미지가 있을 경우 기존 이미지 삭제 후 새 이미지 업로드
        if (profileImageFile != null) {
            // 새 프로필 이미지 업로드
            String storageKey = uploadProfileImage(user.getUserSeq(), profileImageFile);
            deleteProfileImageOnRollback(storageKey);
            // 방금 jOOQ(INSERT…SELECT)로 넣은 행 — 이 트랜잭션의 첫 JPA 로드라 DB 값, 키는 변경 감지로
            requireUser(user.getUserSeq()).changeProfileImageKey(storageKey);
        }

        log.info("{ UserService } : 사용자 등록 완료");
        return new UserSaveResult(user.getUserSeq());
    }

    @Transactional(readOnly = true)
    public UserDetailWithRolesData getCurrentUser(Integer userSeq) {
        log.info("{ UserService } : 사용자 조회");
        UserDetailWithRolesData response = userFindService.findActiveDetailWithRoles(userSeq);
        log.info("사용자 조회: " + response.getUpdateUserSeq());
        log.info("{ UserService } : 사용자 조회 완료");
        return response;
    }

    @Transactional
    public void exists(int tenantSeq, String userId) {
        validateDuplicate(tenantSeq, userId);
    }

    /*
     중복 이메일 검사 — userId 는 테넌트 안에서만 유일
    */
    private void validateDuplicate(Integer tenantSeq, String value) {
        validateDuplicateEmail(tenantSeq, value);
    }

    private void validateDuplicateEmail(Integer tenantSeq, String email) {
        if (userQueryRepository.existsUserId(tenantSeq, email)) {
            throw ApiRuntimeException.type(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    /** 행 잠금(관리 엔티티) — 그 행의 첫 로드에서 부른다(이미 관리 중이면 캐시 값). 판정만 하고 고치지 않으면 커밋 때 UPDATE 없음 */
    @Transactional
    public User lockUserBySeq(Integer userSeq) {
        return Optional.ofNullable(userRepository.lockUser(userSeq))
                .orElseThrow(() -> ApiRuntimeException.type(UserErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public List<User> lockUsersBySeqList(List<Integer> userSeqList) {
        return userRepository.lockUsers(userSeqList);
    }

    @Override
    @Transactional
    public void lockUsersInSeqOrder(Collection<Integer> userSeqs) {
        // IN 한 문장(lockUsers)은 잠그는 순서가 정해지지 않아 행마다 따로 — 정렬 순서가 곧 잠금 순서
        userSeqs.stream().filter(Objects::nonNull).distinct().sorted().forEach(userRepository::lockUser);
    }

    @Transactional
    public void increaseUseQuotaByte(Long additionalBytes, int userSeq) {
        var result = userRepository.addUseQuotaByte(userSeq, additionalBytes);
        DriveAssert.throwIfZero(result, QuotaErrorCode.UPDATE_FAIL);
    }

    @Transactional
    public void decreaseUseQuotaByte(Long additionalBytes, int userSeq) {
        var result = userRepository.addUseQuotaByte(userSeq, additionalBytes == null ? null : -additionalBytes);
        DriveAssert.throwIfZero(result, QuotaErrorCode.DECREASE_FAIL);
    }

    /** 고칠 행(관리 엔티티) — 없으면 USER_NOT_FOUND */
    private User requireUser(Integer userSeq) {
        User user = userSeq == null ? null : userRepository.findById(userSeq);
        DriveAssert.throwIfNull(user, UserErrorCode.USER_NOT_FOUND);
        return user;
    }

    @Transactional
    public void updateUserByAdmin(Integer loginUserSeq, Integer userSeq, AdminUserUpdateDto updateDto) {
        log.info("{ UserService } : 관리자의 사용자 정보 수정 ");

        // 고칠 행 1회 로드 — 호출부(AdminUserUseCase)가 잠금으로 이미 로드했으면 1차 캐시의 같은 인스턴스
        User user = requireUser(userSeq);
        // 현재 키는 DB 값 — 요청의 storageKey 를 믿으면 임의 스토리지 객체를 삭제·프로필로 지정할 수 있다
        String storageKey = processProfileImageChange(userSeq, user.getProfileImageKey(), updateDto.profileImageFile(), updateDto.deleteProfileImage());
        user.changeByAdmin(updateDto.userName(), updateDto.totalQuotaByte(), updateDto.phoneNumber(), storageKey, updateDto.countryCode());

        log.info("{ UserService } : 관리자의 사용자 정보 수정 완료");
    }

    @AuditLog
    @Transactional
    public void updateCurrentUser(Integer loginUserSeq, UserUpdateDto updateDto) {
        log.info("{ UserService } : 사용자 정보 수정 ");

        // 감사 로그 "변경 전" 스냅샷(국가코드/전화번호 비교용)은 update 실행 전에 확보한다.
        UserDetailData beforeUser = userFindService.findDetailByUserSeq(loginUserSeq);

        // 현재 키는 DB 값(요청의 sk 는 쓰지 않음 — updateUserByAdmin 과 같은 이유)
        String storageKey = processProfileImageChange(loginUserSeq, beforeUser.getProfileImageKey(), updateDto.profileImageFile(), updateDto.deleteProfileImage());
        requireUser(loginUserSeq).changeProfile(updateDto.phoneNumber(), updateDto.userLanguage(), updateDto.defaultLandingPage(), storageKey, updateDto.countryCode());

        userAuditService.recordSelfInfoUpdate(beforeUser, updateDto.countryCode(), updateDto.phoneNumber());
        log.info("{ UserService } : 사용자 정보 수정 완료");
    }

    public String uploadProfileImage(Integer userSeq, MultipartFile profileImage) {
        ProfileImageValidator.validateOrThrow(profileImage);
        String storageKey = ProfileType.PROFILE.buildStoragePath(LocaleContextHolder.getLocale(), "user-" + userSeq + ".png");
        try {
            storageService.upload(profileImage, storageKey);
        } catch (StorageException e) {
            log.error("[uploadProfileImage upload fail] : userSeq : {}", userSeq, e);
            throw ApiRuntimeException.type(UserErrorCode.PROFILE_UPLOAD_FAIL);
        }
        return storageKey;
    }

    public String deleteProfileImage(String storageKey) {
        try {
            storageService.delete(storageKey);
        } catch (StorageException e) {
            log.error("[deleteProfileImage delete fail] : storageKey : {}", storageKey, e);
            throw ApiRuntimeException.type(UserErrorCode.PROFILE_DELETE_FAIL);
        }
        return storageKey;
    }

    private String processProfileImageChange(Integer userSeq, String currentStorageKey, MultipartFile file, boolean deleteProfileImage) {
        /**
         * 삭제 요청 시 -> 이미지 제거(커밋 뒤), 스토리지 키 null
         * 새 파일 업로드 시 -> 이미지 교체, 스토리지 키 교체(롤백되면 새 객체 삭제, 키가 바뀌면 이전 객체는 커밋 뒤 삭제)
         * 둘 다 아닌 경우 -> 이미지 유지
         * 스토리지 삭제를 DB 커밋 전에 하면 DB 갱신이 실패했을 때 키만 남은 깨진 아바타가 된다(작업 E10).
         */
        if(deleteProfileImage) {
            if(isOwnProfileImageKey(userSeq, currentStorageKey)) {
                deleteProfileImageAfterCommit(currentStorageKey);
            }
            return null;
        }

        if(file != null) {
            // 파일 o - 새 이미지 업로드, 스토리지 키 변경
            String newStorageKey = uploadProfileImage(userSeq, file);
            if (!newStorageKey.equals(currentStorageKey)) {
                // 같은 키면 이전 내용을 덮어썼으므로 롤백해도 지우지 않는다(지우면 DB 의 기존 키가 깨진다)
                deleteProfileImageOnRollback(newStorageKey);
                if (isOwnProfileImageKey(userSeq, currentStorageKey)) {
                    deleteProfileImageAfterCommit(currentStorageKey);
                }
            }
            return newStorageKey;
        }

        return currentStorageKey;
    }

    /** 커밋 뒤 스토리지 삭제 — DB 가 이미 키를 비웠으므로 실패해도 고아 객체만 남는다. 트랜잭션 동기화가 없으면 바로 삭제. */
    private void deleteProfileImageAfterCommit(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteProfileImage(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.delete(storageKey);
                } catch (RuntimeException e) {
                    log.warn("[deleteProfileImage after commit fail] : storageKey : {}", storageKey, e);
                }
            }
        });
    }

    /**
     * 이 사용자의 프로필 경로({@code …/user-{seq}.png})인지 — 아니면 스토리지에서 지우지 않는다.
     * 예전에는 요청의 storageKey 가 그대로 저장될 수 있었으므로, DB 에 남은 다른 객체 키를 지우지 않기 위한 방어.
     */
    private static boolean isOwnProfileImageKey(Integer userSeq, String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return false;
        }
        String fileName = "user-" + userSeq + ".png";
        return storageKey.equals(ProfileType.PROFILE.buildStoragePath(null, fileName))
                || (storageKey.startsWith(ProfileType.PROFILE.buildStoragePath(null, "")) && storageKey.endsWith(DriveConstants.PATH_SEPARATOR + fileName));
    }

    /** 롤백되면 방금 올린 객체를 지운다 — DB 에 키가 남지 않으므로 고아가 된다. */
    private void deleteProfileImageOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    storageService.delete(storageKey);
                } catch (RuntimeException e) {
                    log.warn("[deleteProfileImage on rollback fail] : storageKey : {}", storageKey, e);
                }
            }
        });
    }
    

    public ProfileImageResult downloadProfileImage(Integer userSeq) {
        UserDetailData user = userFindService.findDetailByUserSeq(userSeq);

        String storageKey = user.getProfileImageKey();
        if (!StringUtils.hasText(storageKey)) {
            return ProfileImageResult.empty();
        }

        String fileName = storageKey.substring(storageKey.lastIndexOf("/") + 1);

        var fileData = storageService.getFileData(storageKey);
        var fileStream = ResourceUtil.createFileStream(fileData);

        return new ProfileImageResult(
                fileName,
                fileData.contentLength(),
                fileData.contentType(),
                fileStream::writeTo
        );
    }

    public ProfileImageResult downloadAnonymousProfileImage(int animal, int color) {
        String classpath = String.format("anonymous-animals/%d_%s.svg",
                animal, ANONYMOUS_ANIMAL_FILE_NAMES[animal - 1]);

        var resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            return ProfileImageResult.empty();
        }

        try {
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String colorHex = CursorColors.getByIndex(color - 1);
            bytes = applySvgColor(bytes, colorHex);

            final byte[] finalBytes = bytes;

            return new ProfileImageResult(
                    animal + ".svg",
                    (long) finalBytes.length,
                    "image/svg+xml",
                    outputStream -> outputStream.write(finalBytes)
            );
        } catch (IOException e) {
            log.error("[downloadAnonymousProfileImage] failed to read classpath: {}", classpath, e);
            return ProfileImageResult.empty();
        }
    }

    private byte[] applySvgColor(byte[] svgBytes, String colorHex) {
        String svg = new String(svgBytes, java.nio.charset.StandardCharsets.UTF_8);
        svg = svg.replace("{{color}}", colorHex);
        return svg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @AuditLog
    @Transactional
    public void updatePassword(Integer loginUserSeq, String loginUserId, String currentPwd, String newPwd, HttpServletRequest httpRequest) {
        tenantPolicy.requireSelfPasswordChangeable(loginUserSeq);
        UserAuthData findUser = userFindService.findAuthByUserSeq(loginUserSeq);

        // currentPwd와 현재 유저 비밀번호 일치하는지 검증 -> 일치하지않으면 실패
        if (currentPwd != null && !findUser.getEncPwd().isSamePassword(currentPwd, ENCODER)) {
            DriveAssert.throwIfFalse(false, UserErrorCode.PASSWORD_NOT_MATCH);
        }

        // newPwd와 현재 유저 비밀번호가 일치하는지 검증 -> 일치하면 실패
        if(findUser.getEncPwd().isSamePassword(newPwd, ENCODER)) {
            DriveAssert.throwIfFalse(false, UserErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        // 판정·감사용 DTO(findUser)와 별도로 고칠 행을 로드 — 비밀번호 + 실패 횟수 초기화(ACTIVE 일 때)를 변경 감지로
        requireUser(loginUserSeq).changePassword(Password.encrypt(newPwd, ENCODER));
        userLogService.registerChangePasswordLog(findUser.getUserSeq(), httpRequest, ActorType.USER);

        // 이벤트는 행위자가 아니라 "대상 계정"의 role로 결정(대상=본인). ADMIN 역할 보유면 ADMIN_PASSWORD_CHANGE, 아니면 USER_PASSWORD_CHANGE(다중 역할 — tb_user_role).
        // 로그인 상태 변경이라 행위자는 인증 정보(SecurityContext)에서 Aspect가 채움.
        EventName eventName = userRoleFindService.holds(findUser.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_PASSWORD_CHANGE : EventName.USER_PASSWORD_CHANGE;
        userAuditService.record(eventName, findUser);
    }

    @Transactional
    public UserAuthData updatePasswordByAdmin(Integer userSeq, String encPwd, HttpServletRequest httpRequest) {
        tenantPolicy.requireManageableUser(userSeq);
        UserAuthData findUser = userFindService.findAuthByUserSeq(userSeq);

        // findUser 는 감사용 변경 전 스냅샷(실패 횟수 포함) — 고칠 행은 따로 로드
        requireUser(userSeq).changePassword(Password.encrypt(encPwd, ENCODER));
        userLogService.registerChangePasswordLog(userSeq, httpRequest, ActorType.ADMIN);

        return findUser;
    }

    // 해당 메서드는 새 트랜잭션으로 분리해서 처리 ex) 로그인 실패로 트랜잭션이 롤백돼도 증가 쿼리는 적용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseLoginFailureCount(Integer userSeq) {
        userRepository.increaseLoginFailureCount(userSeq);
    }

    @Transactional
    public void resetLoginFailureCount(Integer userSeq) {
        userRepository.resetLoginFailureCount(userSeq);
    }

    @Transactional
    public void activateUser(Integer loginUserSeq, Integer userSeq, int tenantSeq, boolean isSeat, int constrainValue) {
        log.info("{ UserService } : 사용자 활성화 ");

        // 없거나 이미 ACTIVE 거나 시트 초과면 0건 → 실패
        int updated = userSeq == null ? 0 : userRepository.activateUser(loginUserSeq, userSeq, tenantSeq, isSeat, constrainValue);
        log.info("[사용자 활성화] userSeq={}, isSeat={}, constrainValue={}, updated={}", userSeq, isSeat, constrainValue, updated);
        DriveAssert.throwIfFalse(updated > 0, UserErrorCode.ACTIVATE_FAIL);

        log.info("{ UserService } : 사용자 활성화 완료");
    }

    @Transactional
    public void deactivateUser(Integer loginUserSeq, Integer userSeq) {
        log.info("{ UserService } : 사용자 비활성화 ");

        if(Objects.equals(loginUserSeq, userSeq) || userSeq == DriveConstants.SUPER_ADMIN_USER_SEQ) {
            throw ApiRuntimeException.type(UserErrorCode.CANNOT_DEACTIVATE_SELF_OR_DEFAULT_ADMIN);
        }

        // 없거나 이미 INACTIVE 면 실패(구 JPQL 0행과 같음). 수정자·수정일은 Auditing
        User user = userSeq == null ? null : userRepository.findById(userSeq);
        DriveAssert.throwIfFalse(user != null && user.deactivate(), UserErrorCode.INACTIVATE_FAIL);
        
        log.info("{ UserService } : 사용자 비활성화 완료");
    }

    /** 없거나 ACTIVE·INACTIVE 가 아니면 실패(구 JPQL 0행과 같음). 수정자·수정일은 Auditing */
    @Transactional
    public void withdraw(int userSeq, Instant pendingDeleteDate, Instant deleteScheduleTime) {
        User user = userRepository.findById(userSeq);
        DriveAssert.throwIfFalse(user != null && user.withdraw(pendingDeleteDate, deleteScheduleTime), UserErrorCode.WITHDRAW_PENDING_FAIL);
    }

    @Transactional
    @Override
    public void setupMfaTotp(int userSeq, String mfaSecretKey) {
        var query = userConverter.toSetupMfaQuery(userSeq, mfaSecretKey);
        var result = userRepository.setupMfaTotp(query);
        DriveAssert.throwIfZero(result, UserErrorCode.MFA_SETUP_FAIL);
    }

    @Transactional
    @Override
    public void confirmMfaTotp(int userSeq, Instant mfaConfirmDate, Instant cutoffAt) {
        var query = userConverter.toConfirmMfaQuery(userSeq, mfaConfirmDate, cutoffAt);
        var result = userRepository.confirmMfaTotp(query);
        DriveAssert.throwIfZero(result, UserErrorCode.MFA_CONFIRM_FAIL);
    }
    
    @Transactional
    @Override
    public int clearPendingMfaTotp(Instant cutoffAt, int updateUserSeq) {
        var query = userConverter.toClearPendingMfaQuery(cutoffAt, updateUserSeq);
        return userRepository.clearPendingMfaTotp(query);
    }
    
    @Transactional
    @Override
    public void cancelMfa(int userSeq, int tenantSeq) {
        // 없거나 사용 중(ENABLED)이 아니거나 다른 테넌트면 실패(구 JPQL 0행과 같음). 수정자·수정일은 Auditing
        User user = userRepository.findById(userSeq);
        DriveAssert.throwIfFalse(user != null && user.cancelMfa(tenantSeq), UserErrorCode.MFA_CANCEL_FAIL);
    }

    @Transactional
    @Override
    public void sendPasswordResetLink(String userId, HttpServletRequest httpRequest) {
        // userId 는 테넌트 안에서만 유일 — 요청 주소의 테넌트에서 찾는다
        sendPasswordResetLink(userQueryRepository.findActiveDetailByUserId(TenantContext.getOrThrow(), userId));
    }

    @Transactional
    @Override
    public void sendPasswordResetLink(int userSeq, HttpServletRequest httpRequest) {
        sendPasswordResetLink(userQueryRepository.findActiveDetailHoldingRole(userSeq, RoleType.USER));
    }

    private void sendPasswordResetLink(UserDetailData user) {
        DriveAssert.throwIfNull(user, UserErrorCode.USER_NOT_FOUND);
        TenantGuard.verifyOwnership(user.getTenantSeq(), UserErrorCode.USER_NOT_FOUND);
        // SYSTEM_MANAGED 보호 계정 비밀번호는 프로비저닝이 정한 비공개 값 — 재설정 메일도 보내지 않는다(SELF_MANAGED 보호 계정은 본인 재설정 허용)
        tenantPolicy.requireSelfPasswordChangeable(user.getUserSeq());
        // 메일을 막은 테넌트(SYSTEM_MANAGED)는 토큰도 만들지 않는다
        tenantPolicy.require(user.getTenantSeq(), TenantFeature.MAIL);

        userRepository.resetLoginFailureCount(user.getUserSeq());
        
        int tenantSeq = user.getTenantSeq();

        String serviceName = brandingMetaService.findServiceNameOrDefault(tenantSeq);

        // 기존 토큰 삭제 (중복 요청 처리)
        passwordResetTokenRepository.deleteByUserSeq(user.getUserSeq());

        // 토큰 생성 및 해시화 후 저장 (1시간 유효)
        Instant now = Instant.now();
        String token = Base62TokenGenerator.generate(32);
        String tokenHash = TokenHasher.hash(token);
        passwordResetTokenRepository.save(PasswordResetToken.of(user.getUserSeq(), tokenHash, now, now.plus(passwordResetExpiry)));

        // 이벤트에는 원본 token 전달 (이메일 링크용)
        Locale locale = LocaleContextHolder.getLocale();
        UserPasswordResetRequestedEvent userPasswordResetRequestedEvent = new UserPasswordResetRequestedEvent(user.getUserId(), user.getUserName(), serviceName, token, locale, tenantSeq);
        publisher.publishEvent(userPasswordResetRequestedEvent);
    }

    @Override
    public void verifyPasswordResetToken(String token) {
        resolveValidResetToken(token);
    }

    @AuditLog
    @Transactional
    @Override
    public void resetPasswordByToken(String token, String newPwd, HttpServletRequest httpRequest) {

        var resetContext = resolveValidResetToken(token);
        tenantPolicy.requireSelfPasswordChangeable(resetContext.user().getUserSeq());

        // 1회용 보장 — 토큰을 먼저 소비(삭제 0행 = 이미 소비, 동시 요청 포함)하고 나서 비밀번호를 바꾼다
        DriveAssert.throwIfZero(passwordResetTokenRepository.deleteByTokenHash(resetContext.passwordResetToken().getTokenHash()), UserErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        // 미인증 경로 — Auditing 수정자는 시큐리티 사용자가 없어 이전 값 유지, 수정일만 갱신
        requireUser(resetContext.passwordResetToken().getUserSeq()).changePassword(Password.encrypt(newPwd, ENCODER));
        userLogService.registerChangePasswordLog(resetContext.passwordResetToken().getUserSeq(), httpRequest, ActorType.USER);

        // 이벤트는 "대상 계정"의 role로 결정(대상=본인). ADMIN 역할 보유면 ADMIN_PASSWORD_CHANGE, 아니면 USER_PASSWORD_CHANGE(다중 역할 — tb_user_role).
        // 미인증(SecurityContext 비어있음)이라 행위자를 Aspect가 채울 수 없어 대상 본인을 행위자로 직접 override(LOGIN 경로와 동일 패턴).
        UserDetailData user = resetContext.user();
        EventName eventName = userRoleFindService.holds(user.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_PASSWORD_CHANGE : EventName.USER_PASSWORD_CHANGE;
        userAuditService.recordSelfActed(eventName, user);
    }

    private record ResetContext(PasswordResetToken passwordResetToken, UserDetailData user) {}

    private ResetContext resolveValidResetToken(String token) {
        String tokenHash = TokenHasher.hash(token);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash);
        DriveAssert.throwIfNull(resetToken, UserErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        DriveAssert.throwIfTrue(Instant.now().isAfter(resetToken.getExpirationDate()), UserErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);

        UserDetailData user = userFindService.findDetailByUserSeq(resetToken.getUserSeq());
        TenantGuard.verifyOwnership(user.getTenantSeq(), UserErrorCode.USER_NOT_FOUND);
        return new ResetContext(resetToken, user);
    }

}
