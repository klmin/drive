package com.thinkfree.drive.resource.version.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ResourceVersionDeleteSeqListQuery {
    
    private List<Long> resourceSeqList;
    private Integer userSeq;
    
    
}
