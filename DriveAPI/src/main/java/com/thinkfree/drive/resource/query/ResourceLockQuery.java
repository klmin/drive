package com.thinkfree.drive.resource.query;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceLockQuery {

    private Long resourceSeq;
    private Integer userSeq;
    private ResourceType resourceType;
    private Long parentFolderSeq;
    private String originalFileName;
    private Boolean isDelete;
    
}
