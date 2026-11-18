package com.thinkfree.drive.api.v1.admin.user.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;

import java.time.Instant;

public record AdminUserLogResponse(
        Long userLogSeq,
        Integer userSeq,
        String region,
        String ipAddress,
        String activityType,
        String actorType,
        @DriveDateTimeFormat
        Instant registerDate
) {
}
