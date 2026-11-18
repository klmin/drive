package com.thinkfree.drive.recent.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class RecentDeleteResourcesQuery {
    private Integer userSeq;
    private Integer sharedByUserSeq;
    private List<Long> resourceSeqList;
    private List<Integer> excludeUserSeqList;
    private Integer tenantSeq;
}