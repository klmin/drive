package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ResourcePathUpdateQuery {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Integer changeUserSeq;
    private String oldFullPath;
    private String newFullPath;
    private String oldFullPathSeq;
    private String newFullPathSeq;
    private Boolean isDelete;
    private Integer updateUserSeq;
    private Instant updateDate;
   
    
}
