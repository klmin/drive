package com.thinkfree.drive.share.target.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareTargetDeleteResourcesQuery {
    private Integer sharedByUserSeq;
    private List<Long> resourceSeqList;
}