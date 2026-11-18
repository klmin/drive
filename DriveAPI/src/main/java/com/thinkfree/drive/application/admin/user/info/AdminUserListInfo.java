package com.thinkfree.drive.application.admin.user.info;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * 관리자용 사용자 목록 항목 — 웹 응답(AdminUserListResponse)과 필드 동형.
 * 도메인 UserListData의 quota 필드는 웹 응답에 없어 여기서도 제외한다.
 */
public record AdminUserListInfo
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    /** 보유 역할(tb_user_role) — 역할 순서(ADMIN, USER) */
    List<RoleType> roles,
    Integer registerUserSeq,
    Instant registerDate,
    Integer updateUserSeq,
    Instant updateDate,
    Instant lastLoginDate,
    /** 보호 계정 — 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경이 거부된다(USER_034). 본인 비밀번호 변경은 SYSTEM_MANAGED 테넌트에서만 거부(TENANT_011) */
    Boolean isProtected
)
{
}
