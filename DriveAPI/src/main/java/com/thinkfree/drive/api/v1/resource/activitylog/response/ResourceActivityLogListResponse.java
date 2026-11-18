package com.thinkfree.drive.api.v1.resource.activitylog.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ResourceActivityLogListResponse {

    private Long activityLogSeq;
    private String actionType;
    private Integer registerUserSeq;
    private Long registerResourceSeq;

    @DriveDateTimeFormat
    private String registerDate;
    private Object description; // 임시로 Object 사용
}