package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceDeleteParentListQuery  {
    
    private Long resourceSeq;
    private Integer userSeq;
    
}
