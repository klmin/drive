package com.thinkfree.drive.share.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareDeleteResourcesQuery {
    private Integer userSeq;
    private List<Long> resourceSeqList;
    
}
