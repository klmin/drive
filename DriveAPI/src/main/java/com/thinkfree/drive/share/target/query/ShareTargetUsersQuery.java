package com.thinkfree.drive.share.target.query;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareTargetUsersQuery {
    private Integer sharedByUserSeq;
    private Integer targetUserSeq;
    private List<Long> shareSeqList;
    private Long resourceSeq;
    private Boolean isDelete;
}
