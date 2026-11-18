package com.thinkfree.drive.application.share.info;

import com.thinkfree.drive.user.enums.UserStatus;

/** 공유 관련 사용자 1건 — 대상 유저 목록·후보 검색 공용 (웹 응답 항목과 동형 계약). */
public record ShareUserInfo
(
    Integer userSeq,
    String userId,
    String userName,
    UserStatus userStatus,
    Integer tenantSeq,
    String tenantName
)
{
}
