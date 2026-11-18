package com.thinkfree.drive.share.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ShareDeleteResourceQuery {
    private Integer userSeq;
    private Long resourceSeq;
}
