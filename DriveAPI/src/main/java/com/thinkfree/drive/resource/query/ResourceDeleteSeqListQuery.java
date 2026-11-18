package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ResourceDeleteSeqListQuery {
    
    private List<Long> resourceSeqList;
    private Integer userSeq;
    
}
