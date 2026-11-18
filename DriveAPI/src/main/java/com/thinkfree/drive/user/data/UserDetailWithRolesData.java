package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 계정 값 + 보유 역할 전부(tb_user_role, enum 순서)를 한 번에 읽은 결과 — 응답에 계정 값과 roles 를 같이 내는 단건 조회 전용
 * (관리자 단건 조회, 내 정보). 계정 값은 {@link UserDetailData} 와 같아 그 자리에 그대로 쓸 수 있다.
 * 역할이 필요 없는 조회는 {@link UserDetailData} 를 쓴다(서브쿼리를 붙이지 않음).
 */
@Getter
public class UserDetailWithRolesData extends UserDetailData {

    private final List<RoleType> roles;
    /** 보호 계정(tb_user.is_protected) — 막는 판정은 TenantPolicy(SYSTEM_MANAGED 만), 여기는 표시용 */
    private final Boolean isProtected;

    public UserDetailWithRolesData(Integer userSeq, String userId, String userName, UserStatus status,
                                   Long totalQuotaByte, Long useQuotaByte, Integer registerUserSeq, Instant registerDate,
                                   Integer updateUserSeq, Instant updateDate, UserLanguage userLanguage, String countryCode, String phoneNumber,
                                   DefaultLandingPage defaultLandingPage, String profileImageKey, Integer tenantSeq, MfaStatus mfaStatus,
                                   List<RoleType> roles, Boolean isProtected) {
        super(userSeq, userId, userName, status, totalQuotaByte, useQuotaByte, registerUserSeq, registerDate,
                updateUserSeq, updateDate, userLanguage, countryCode, phoneNumber, defaultLandingPage, profileImageKey, tenantSeq, mfaStatus);
        this.roles = roles;
        this.isProtected = isProtected;
    }
}
