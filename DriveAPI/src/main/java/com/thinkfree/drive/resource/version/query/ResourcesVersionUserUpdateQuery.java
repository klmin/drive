package com.thinkfree.drive.resource.version.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
public class ResourcesVersionUserUpdateQuery {

    private Integer userSeq;
    private List<Long> resourceSeqList;
    private Integer changeUserSeq;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
