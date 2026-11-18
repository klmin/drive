package com.thinkfree.drive.permission.query;

import com.thinkfree.drive.partition.PartitionKey;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResourcePermissionQuery {
    private Integer userSeq;
    private PartitionKey key;
    private Boolean isDelete;
}
