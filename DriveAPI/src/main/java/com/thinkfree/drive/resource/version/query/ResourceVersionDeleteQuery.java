package com.thinkfree.drive.resource.version.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceVersionDeleteQuery {
    
    private Long resourceVersionSeq;
    private Integer userSeq;
    private Long resourceSeq;
    
}
