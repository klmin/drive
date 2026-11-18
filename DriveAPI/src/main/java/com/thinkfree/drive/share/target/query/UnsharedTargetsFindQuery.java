package com.thinkfree.drive.share.target.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UnsharedTargetsFindQuery {
    private List<Integer> targetUserSeqList;
    private Integer userSeq;
    private Long shareSeq;
    private Boolean isDelete;
}
