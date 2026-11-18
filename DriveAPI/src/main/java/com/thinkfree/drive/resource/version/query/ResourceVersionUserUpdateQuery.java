package com.thinkfree.drive.resource.version.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ResourceVersionUserUpdateQuery {

    private Integer userSeq;
    private Long resourceSeq;
    private Integer changeUserSeq;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
