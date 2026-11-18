package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ResourceDeleteStateQuery {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Boolean isDelete;
    private String fullPathSeq;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
