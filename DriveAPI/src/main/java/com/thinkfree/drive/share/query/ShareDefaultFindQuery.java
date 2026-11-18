package com.thinkfree.drive.share.query;

import lombok.experimental.SuperBuilder;
import lombok.Getter;

@SuperBuilder
@Getter
public class ShareDefaultFindQuery {
    private Long shareSeq;
    private Integer userSeq;
    private Boolean isDelete;
}
