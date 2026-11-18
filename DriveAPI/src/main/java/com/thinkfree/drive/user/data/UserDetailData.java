package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 계정 값(비밀값 제외) — 읽기 전용. 신원(시큐리티 principal·API Key actor·감사 대상/행위자)과 상세(내 정보·관리자 상세·프로필 키·용량)를 함께 쓴다(10-12 — PK 한 행이라 용도별로 나누지 않음).
 * 비밀값(encPwd·mfaSecretKey)이 필요한 인증 경로는 {@link UserAuthData}.
 * getter 이름은 엔티티 {@code User} 와 같다(그 값을 읽는 기존 로그 문 무변경).
 */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(UserJpaRepository#SELECT_DETAIL) — 필드 순서를 바꾸면 JPQL 도 맞출 것
public class UserDetailData implements UserIdentity {

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
}
