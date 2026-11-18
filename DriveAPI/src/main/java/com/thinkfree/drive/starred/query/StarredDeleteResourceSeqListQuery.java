package com.thinkfree.drive.starred.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class StarredDeleteResourceSeqListQuery {
    private List<Long> resourceSeqList;
    private Integer ownerSeq;
}
