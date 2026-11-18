package com.thinkfree.drive.starred.query;

import com.thinkfree.drive.partition.PartitionKey;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class StarredDeleteShareTargetsQuery {
    private Integer sharedByUserSeq;
    private List<PartitionKey> keys;
}
