package com.thinkfree.drive.share.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SharedResourceNearestFindQuery {
    private Long resourceSeq;
    private Long shareSeq;
    private Long rootResourceSeq;
    private Integer userSeq;
    private Boolean isDelete;
}
