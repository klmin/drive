    package com.thinkfree.drive.partition;
    
    public record ShareTargetPartitionKey
    (
        int userSeq,
        long resourceSeq,
        int sharedByUserSeq
    )
    {
    }
