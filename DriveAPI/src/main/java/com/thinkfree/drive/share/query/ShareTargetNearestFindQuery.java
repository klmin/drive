package com.thinkfree.drive.share.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareTargetNearestFindQuery {
    private Integer userSeq;
    private Long shareSeq;
    private List<Integer> targetUserSeqList;
    private Boolean isDelete;
}
