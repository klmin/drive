package com.thinkfree.drive.resource.query;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ResourceHierarchyQuery {

    private List<Long> resourceSeqList;
    private Integer userSeq;
    private ResourceType resourceType;
    private Boolean isDelete;
}
