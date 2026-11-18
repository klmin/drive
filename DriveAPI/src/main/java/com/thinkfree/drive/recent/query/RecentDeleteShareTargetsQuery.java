package com.thinkfree.drive.recent.query;

import com.thinkfree.drive.partition.PartitionKey;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class RecentDeleteShareTargetsQuery {
    private Integer sharedByUserSeq;
    private List<PartitionKey> keys;
}
