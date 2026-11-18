package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
public class ResourcesUserUpdateQuery {

    private Integer userSeq;
    private Integer changeUserSeq;
    private List<Long> resourceSeqList;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
