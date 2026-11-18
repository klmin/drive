package com.thinkfree.drive.partition;

public record PartitionKey
(
        int sharedByUserSeq,
        long resourceSeq
)
{
}
