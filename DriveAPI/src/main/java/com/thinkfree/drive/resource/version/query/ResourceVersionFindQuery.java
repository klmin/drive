package com.thinkfree.drive.resource.version.query;

import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceVersionFindQuery {
    
    private Long resourceVersionSeq;
    private Long resourceSeq;
    private Integer userSeq;
    private ResourceVersionStatus status;

}
