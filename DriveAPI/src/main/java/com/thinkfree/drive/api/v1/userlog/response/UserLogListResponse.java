package com.thinkfree.drive.api.v1.userlog.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.userlog.data.UserLogData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserLogListResponse {

    private Long userLogSeq;
    private Integer userSeq;
    private String region;
    private String ipAddress;
    private String activityType;
    private String actorType;

    @DriveDateTimeFormat
    private Instant registerDate;

    public static UserLogListResponse from(UserLogData data) {
        return new UserLogListResponse(
                data.getUserLogSeq(),
                data.getUserSeq(),
                data.getRegion(),
                data.getIpAddress(),
                data.getActivityType(),
                data.getActorType(),
                data.getRegisterDate()
        );
    }
}
