package com.thinkfree.drive.share.target.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ShareTargetDeleteResourceQuery {
    private int sharedByUserSeq;
    private Long resourceSeq;
}