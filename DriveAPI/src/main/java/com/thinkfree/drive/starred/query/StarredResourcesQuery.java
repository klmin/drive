package com.thinkfree.drive.starred.query;

import lombok.Builder;

import java.util.List;

@Builder
public class StarredResourcesQuery {
    
    private List<Long> resourceSeqList;
    private Integer userSeq;
    private Integer sharedByUserSeq;
    
}


