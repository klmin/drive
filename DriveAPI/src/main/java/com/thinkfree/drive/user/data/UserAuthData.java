package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 인증용 계정 값 = {@link UserDetailData} 전 필드 + 비밀값(비밀번호 해시·MFA 비밀키)·로그인 실패 수·MFA 방식.
 * 비밀값을 담으므로 인증 계층(로그인·MFA·비밀번호 변경) 밖으로 내보내지 않는다 — 화면·감사 경로는 {@link UserDetailData}.
 * getter 이름은 엔티티 {@code User} 와 같다.
 */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(UserJpaRepository#SELECT_AUTH) — 필드 순서를 바꾸면 JPQL 도 맞출 것
public class UserAuthData implements UserIdentity {

    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private Integer registerUserSeq;
    private Instant registerDate;
    private Integer updateUserSeq;
    private Instant updateDate;
    private UserLanguage userLanguage;
    private String countryCode;
    private String phoneNumber;
    private DefaultLandingPage defaultLandingPage;
    private String profileImageKey;
    private Integer tenantSeq;
    private MfaStatus mfaStatus;
    private Password encPwd;
    private Integer loginFailureCount;
    private MfaMethod mfaMethod;
    private String mfaSecretKey;
}
