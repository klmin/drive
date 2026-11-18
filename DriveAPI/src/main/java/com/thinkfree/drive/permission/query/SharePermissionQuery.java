package com.thinkfree.drive.permission.query;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SharePermissionQuery {
    
    private Long shareSeq;
    private Integer userSeq;
    private Integer requestUserSeq;
    private Boolean isDelete;
}
