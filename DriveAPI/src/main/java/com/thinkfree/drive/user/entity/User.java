package com.thinkfree.drive.user.entity;

import com.thinkfree.drive.global.jpa.DefaultLandingPageConverter;
import com.thinkfree.drive.global.jpa.PasswordConverter;
import com.thinkfree.drive.global.jpa.UserLanguageConverter;
import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.user.enums.*;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;
import java.util.Objects;

/**
 * tb_user 엔티티.
 * <p>
 * 감사 4필드는 {@link BaseAuditEntity}(10-07 2차 패스). 읽기는 Query 리포 DTO(JPQL 생성자 식), 단건 변경은 쓰기 리포 {@code findById}·잠금으로 받은 관리 엔티티의 변경 메서드 → 커밋 때 변경 감지(수정 감사는 Auditing, W7).
 * 남은 JPQL {@code @Modifying} = 원자적 증감·빈번한 단일 컬럼·조건부 다건·MFA 조건부 원자 판정. jOOQ = 시트 검사가 붙은 INSERT…SELECT(원자적 카운트 비교) — JPA 로 INSERT 하지 않으므로
 * 등록 감사는 INSERT…SELECT 가 {@code JdbcAudit} 로 채운다.
 * {@link DynamicUpdate} 로 바뀐 컬럼만 UPDATE — 사용량·로그인 실패 횟수 같은 JPQL 원자적 증감 컬럼을 덮어쓰지 않는다.
 */
@Entity
@DynamicUpdate
@Table(name = "tb_user")
@Getter
@SuperBuilder
@NoArgsConstructor
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userSeq;
    private String userId;
    @Convert(converter = PasswordConverter.class)
    private Password encPwd;
    private String userName;
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private String profileImageKey;
    private String countryCode;
    private String phoneNumber;
    @Convert(converter = UserLanguageConverter.class)
    private UserLanguage userLanguage;
    private Integer loginFailureCount;
    /** 마지막 로그인 실패(시도) 시각 — 잠금 해제 판단(LoginLockPolicy). 쓰기는 DefaultUserRepository#countLoginAttempt */
    private Instant loginFailedAt;
    @Deprecated
    private String isDelete;
    private Instant deleteScheduleTime;
    private Instant pendingDeleteDate;
    private Instant deleteDate;
    @Convert(converter = DefaultLandingPageConverter.class)
    private DefaultLandingPage defaultLandingPage;
    @Enumerated(EnumType.STRING)
    private MfaStatus mfaStatus;
    @Enumerated(EnumType.STRING)
    private MfaMethod mfaMethod;
    private String mfaSecretKey;
    private Instant mfaSetupDate;
    private Instant mfaConfirmDate;
    private Integer tenantSeq;
    @Enumerated(EnumType.STRING)
    private SignupType signupType;
    /** 보호 계정(테넌트 생성 시 만든 관리자 계정). 탈퇴·삭제·비활성·역할·관리자 비밀번호 변경은 테넌트 유형과 관계없이 거부(USER_034), 본인 비밀번호 변경 거부는 SYSTEM_MANAGED 테넌트에서만. INSERT 는 jOOQ(null = N) */
    @Convert(converter = YesNoConverter.class)
    private Boolean isProtected;

    private User(String userId, Integer tenantSeq, Password encPwd, SignupType signupType, String userName, UserStatus status, Long totalQuotaByte,
                 Integer registerUserSeq, String profileImageKey, String countryCode, String phoneNumber) {
        this.userId = userId;
        this.tenantSeq = tenantSeq;
        this.encPwd = encPwd;
        this.signupType = signupType;
        this.userName = userName;
        this.status = status;
        this.totalQuotaByte = totalQuotaByte;
        assignRegisterAudit(registerUserSeq, null);
        this.profileImageKey = profileImageKey;
        this.countryCode = countryCode;
        this.phoneNumber = phoneNumber;
    }

    // 추후 이미지 설정 가능하도록 변경 필요
    public static User createUser(String userId, Integer tenantSeq, Password encPwd, SignupType signupType, String userName, UserStatus status, Long totalQuotaByte, Integer registerUserSeq, String profileImageKey, String countryCode, String phoneNumber) {
        return new User(userId, tenantSeq, encPwd, signupType, userName, status, totalQuotaByte, registerUserSeq, profileImageKey, countryCode, phoneNumber);
    }

    /** INSERT 전 보호 계정 표시 — 테넌트 생성 시 만드는 관리자 계정 전용. 나중에 보호로 바꾸는 경로는 없다 */
    public void markProtected() {
        this.isProtected = true;
    }

    /** INSERT…SELECT(시트 검사) 후 DB 가 발급한 PK 주입 — 리포지토리 전용(MyBatis useGeneratedKeys 의 keyProperty 대체). */
    public void assignUserSeq(Integer userSeq) {
        this.userSeq = userSeq;
    }

    // ------------------------------------------------------------------ 변경 메서드 — Service 가 쓰기 리포에서 로드한 관리 엔티티에 호출, 커밋 때 변경 감지(감사는 Auditing, W7)

    /** 관리자 수정(이름·총 용량·전화·프로필 이미지·국가) */
    public void changeByAdmin(String userName, Long totalQuotaByte, String phoneNumber, String profileImageKey, String countryCode) {
        this.userName = userName;
        this.totalQuotaByte = totalQuotaByte;
        this.phoneNumber = phoneNumber;
        this.profileImageKey = profileImageKey;
        this.countryCode = countryCode;
    }

    /** Super-admin managed SINGLE quota synchronization; caller holds tenant and user locks. */
    public void changeTotalQuota(long totalQuotaByte) {
        this.totalQuotaByte = totalQuotaByte;
    }

    /** 본인 수정(전화·언어·기본 화면·프로필 이미지·국가) */
    public void changeProfile(String phoneNumber, UserLanguage userLanguage, DefaultLandingPage defaultLandingPage, String profileImageKey, String countryCode) {
        this.phoneNumber = phoneNumber;
        this.userLanguage = userLanguage;
        this.defaultLandingPage = defaultLandingPage;
        this.profileImageKey = profileImageKey;
        this.countryCode = countryCode;
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    /** 비밀번호 변경 + 로그인 실패 횟수 초기화(ACTIVE 일 때만 — 구 resetLoginFailureCount(userId, ACTIVE) 와 같은 조건) */
    public void changePassword(Password encPwd) {
        this.encPwd = encPwd;
        if (status == UserStatus.ACTIVE) {
            this.loginFailureCount = 0;
        }
    }

    /** 비활성 — 이미 INACTIVE 면 false(변경 없음) */
    public boolean deactivate() {
        if (status == UserStatus.INACTIVE) {
            return false;
        }
        this.status = UserStatus.INACTIVE;
        return true;
    }

    /** 탈퇴(삭제 대기) — ACTIVE·INACTIVE 가 아니면 false(변경 없음) */
    public boolean withdraw(Instant pendingDeleteDate, Instant deleteScheduleTime) {
        if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) {
            return false;
        }
        this.status = UserStatus.PENDING_DELETE;
        this.pendingDeleteDate = pendingDeleteDate;
        this.deleteScheduleTime = deleteScheduleTime;
        return true;
    }

    /** 탈퇴 철회 — PENDING_DELETE 가 아니면 false(변경 없음), 비활성으로 */
    public boolean restore() {
        if (status != UserStatus.PENDING_DELETE) {
            return false;
        }
        this.status = UserStatus.INACTIVE;
        this.pendingDeleteDate = null;
        this.deleteScheduleTime = null;
        return true;
    }

    /** MFA 해제 — 사용 중(ENABLED)이고 같은 테넌트가 아니면 false(변경 없음) */
    public boolean cancelMfa(int tenantSeq) {
        if (mfaStatus != MfaStatus.ENABLED || !Objects.equals(this.tenantSeq, tenantSeq)) {
            return false;
        }
        this.mfaStatus = MfaStatus.DISABLED;
        this.mfaMethod = null;
        this.mfaSecretKey = null;
        this.mfaSetupDate = null;
        this.mfaConfirmDate = null;
        return true;
    }

    public boolean wouldExceedQuota(long additionalBytes) {
        return totalQuotaByte - useQuotaByte - additionalBytes < 0;
    }

    public boolean isInvalidTotalQuotaChange(long newTotalQuotaByte) {
        return useQuotaByte > newTotalQuotaByte;
    }

}
