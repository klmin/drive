package com.thinkfree.drive.application.admin.info;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 단건 조회 결과 — 웹 응답(AdminResponse)과 필드 동형.
 * 날짜는 Instant으로 담고, 포맷("yyyy. M. d. HH:mm")은 웹 어댑터(AdminResponseConverter)가 수행한다.
 */
public record AdminUserDetailInfo
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    Long totalQuotaByte,
    Long useQuotaByte,
    List<RoleType> roles,
    Integer registerUserSeq,
    Instant registerDate,
    Integer updateUserSeq,
    Instant updateDate,
    String countryCode,
    String phoneNumber,
    String profileImageKey,
    Boolean isMfaEnabled,
    /** 보호 계정 — 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경이 거부된다(USER_034). 본인 비밀번호 변경은 SYSTEM_MANAGED 테넌트에서만 거부(TENANT_011) */
    Boolean isProtected
)
{
}
