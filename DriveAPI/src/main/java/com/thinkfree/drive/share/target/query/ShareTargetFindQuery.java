package com.thinkfree.drive.share.target.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ShareTargetFindQuery {
    private Long shareSeq;
    private Integer sharedByUserSeq;
}
