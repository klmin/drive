package com.thinkfree.drive.userlog.data;

import com.thinkfree.drive.userlog.enums.ActivityType;
import com.thinkfree.drive.userlog.enums.ActorType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자 로그 목록 행(구 UserLogMapper.findAll resultMap). activityType·actorType 은 응답 호환을 위해 문자열(enum 이름) 유지.
 * JPQL 생성자 식({@code select new …UserLogData(l.userLogSeq, l.userSeq, l.region, l.ipAddress, l.activityType, l.actorType, l.registerDate)})이 쓰는 생성자 — 인자 순서를 바꾸면 {@code UserLogJpaRepository.findAll} 의 JPQL 도 함께.
 */
@Getter
@NoArgsConstructor
public class UserLogData {
    private Long userLogSeq;
    private Integer userSeq;
    private String region;
    private String ipAddress;
    private String activityType;
    private String actorType;
    private Instant registerDate;

    public UserLogData(Long userLogSeq, Integer userSeq, String region, String ipAddress, ActivityType activityType, ActorType actorType, Instant registerDate) {
        this.userLogSeq = userLogSeq;
        this.userSeq = userSeq;
        this.region = region;
        this.ipAddress = ipAddress;
        this.activityType = activityType == null ? null : activityType.name();
        this.actorType = actorType == null ? null : actorType.name();
        this.registerDate = registerDate;
    }
}
