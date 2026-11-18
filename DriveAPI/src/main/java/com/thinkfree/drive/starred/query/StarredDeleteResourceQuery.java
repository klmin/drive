package com.thinkfree.drive.starred.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class StarredDeleteResourceQuery {
    private Integer userSeq;
    private Integer sharedByUserSeq;
    private Long resourceSeq;
    private List<Integer> excludeUserSeqList;
}