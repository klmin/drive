package com.thinkfree.drive.user.service;

import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.dto.AdminUserUpdateDto;
import com.thinkfree.drive.user.dto.ProfileImageResult;
import com.thinkfree.drive.user.dto.UserSaveResult;
import com.thinkfree.drive.user.dto.UserUpdateDto;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collection;
import java.util.List;


public interface UserService {

    UserSaveResult registerUser(AdminUserSaveDto saveDto, MultipartFile profileImageFile);

    /** 테넌트 생성 시 관리자 계정 — 보호 계정(is_protected='Y')으로 등록. 그 외는 registerUser 와 같다 */
    UserSaveResult registerTenantAdmin(AdminUserSaveDto saveDto);

    /** 내 정보 — 계정 값 + 보유 역할 전부(1회 조회), ACTIVE 아니면 USER_NOT_FOUND */
    UserDetailWithRolesData getCurrentUser(Integer userSeq);

    /** 같은 테넌트에 userId 를 쓰는 삭제되지 않은 계정이 있으면 DUPLICATE_EMAIL(userId 는 테넌트 안에서만 유일) */
    void exists(int tenantSeq, String userId);

    /** 행 잠금 — 관리 엔티티를 그대로 반환(예외): 호출부가 같은 트랜잭션에서 그 인스턴스로 판정·변경 감지 수정을 한다 */
    User lockUserBySeq(Integer userSeq);

    /** 행 잠금(관리 엔티티 목록) — 그 행들의 첫 로드에서. 관리 엔티티 반환(예외)은 {@link #lockUserBySeq} 와 같은 이유 */
    List<User> lockUsersBySeqList(List<Integer> userSeqList);

    /**
     * 여러 사용자 행을 user_seq 오름차순으로 하나씩 잠근다(중복·null 제외, 없는 사용자는 건너뜀 — 존재 판정은 호출부의 원래 흐름에 맡긴다).
     * 사용자 → 리소스 잠금 순서를 지키려고 리소스를 건드리기 전에 부른다 — 순서를 고정해 반대 방향 동시 요청끼리도 교착하지 않는다(10-14, 소유자가 바뀌는 이동).
     */
    void lockUsersInSeqOrder(Collection<Integer> userSeqs);

    void increaseUseQuotaByte(Long additionalBytes, int userSeq);

    void decreaseUseQuotaByte(Long additionalBytes, int userSeq);

    void updateUserByAdmin(Integer loginUserSeq, Integer userSeq, AdminUserUpdateDto updateDto);

    void updateCurrentUser(Integer loginUserSeq, UserUpdateDto updateDto);

    String uploadProfileImage(Integer userSeq, MultipartFile profileImage);

    ProfileImageResult downloadProfileImage(Integer userSeq);

    ProfileImageResult downloadAnonymousProfileImage(int animal, int color);

    void updatePassword(Integer userSeq, String userId, String encPwd, String newPwd, HttpServletRequest httpRequest);

    UserAuthData updatePasswordByAdmin(Integer userSeq, String encPwd, HttpServletRequest httpRequest);

    void increaseLoginFailureCount(Integer userSeq);

    void resetLoginFailureCount(Integer userSeq);

    void activateUser(Integer loginUserSeq, Integer userSeq, int tenantSeq, boolean isSeat, int constrainValue);

    void deactivateUser(Integer loginUserSeq, Integer userSeq);

    void withdraw(int userSeq, Instant pendingDeleteDate, Instant deleteScheduleTime);
    
    void setupMfaTotp(int userSeq, String mfaSecretKey);
    
    void confirmMfaTotp(int userSeq, Instant mfaConfirmData, Instant cutoffAt);

    int clearPendingMfaTotp(Instant cutoffAt, int updateUserSeq);

    void cancelMfa(int userSeq, int tenantSeq);

    /** 로그인 화면 요청 — 현재 테넌트(TenantContext)에서 userId 로 찾는다 */
    void sendPasswordResetLink(String userId, HttpServletRequest httpRequest);

    /** 설정 화면 요청 — 로그인한 본인(userSeq) */
    void sendPasswordResetLink(int userSeq, HttpServletRequest httpRequest);

    void verifyPasswordResetToken(String token);

    void resetPasswordByToken(String token, String newPwd, HttpServletRequest httpRequest);

}
