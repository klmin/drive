package com.thinkfree.drive.permission.query;

import com.thinkfree.drive.partition.PartitionKey;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResourcesPermissionQuery {
    private Integer userSeq;
    private List<PartitionKey> keys;
    private Boolean isDelete;
}
