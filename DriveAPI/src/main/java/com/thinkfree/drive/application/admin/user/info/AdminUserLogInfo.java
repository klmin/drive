package com.thinkfree.drive.application.admin.user.info;

import java.time.Instant;

/** 사용자 Log 항목 — 웹 응답(AdminUserLogResponse)과 필드 동형 */
public record AdminUserLogInfo
(
    Long userLogSeq,
    Integer userSeq,
    String region,
    String ipAddress,
    String activityType,
    String actorType,
    Instant registerDate
)
{
}
