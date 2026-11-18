package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ResourceRestoreChildQuery{

    private Long resourceSeq;
    private Integer userSeq;
    private String oldFullPath;
    private String newFullPath;
    private String oldFullPathSeq;
    private String newFullPathSeq;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
