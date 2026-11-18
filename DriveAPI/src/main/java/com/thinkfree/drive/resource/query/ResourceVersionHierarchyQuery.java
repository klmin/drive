package com.thinkfree.drive.resource.query;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceVersionHierarchyQuery {
    private Long resourceSeq;
    private Integer userSeq;
    private ResourceType resourceType;
    private Boolean isDelete;
}
