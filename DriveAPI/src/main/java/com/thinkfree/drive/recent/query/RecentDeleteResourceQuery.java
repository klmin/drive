package com.thinkfree.drive.recent.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class RecentDeleteResourceQuery {
    private Integer userSeq;
    private Integer sharedByUserSeq;
    private Long resourceSeq;
    private List<Integer> excludeUserSeqList;
}
