package com.thinkfree.drive.resource.query;

import com.thinkfree.drive.partition.PartitionKey;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ResourceHierarchyPartitionKeysQuery {

    private List<PartitionKey> keys;
    private Boolean isDelete;
}
