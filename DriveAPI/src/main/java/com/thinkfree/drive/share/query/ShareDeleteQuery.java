package com.thinkfree.drive.share.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ShareDeleteQuery {
    private Long shareSeq;
    private Integer userSeq;
}
