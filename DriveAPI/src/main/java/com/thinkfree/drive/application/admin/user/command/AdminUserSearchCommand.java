package com.thinkfree.drive.application.admin.user.command;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/** 사용자 검색 커맨드 — 웹 요청(AdminUserSearchRequest)과 동형. userId·userName 중 하나만 걸려도 검색된다 */
public record AdminUserSearchCommand
(
    String searchKeyword,
    /* 미지정(null·빈 목록) 시 USER 만 */
    List<RoleType> roleTypes,
    /* 미지정(null·빈 목록) 시 ACTIVE 만 */
    List<UserStatus> userStatuses
)
{
}
