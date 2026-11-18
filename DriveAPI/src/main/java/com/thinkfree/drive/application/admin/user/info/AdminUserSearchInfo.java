package com.thinkfree.drive.application.admin.user.info;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/** 사용자 검색 항목 — 웹 응답(AdminUserSearchResponse)과 필드 동형. roles는 보유 역할(enum 순서) */
public record AdminUserSearchInfo
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus status,
    List<RoleType> roles,
    Boolean isProtected
)
{
}
