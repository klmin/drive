package com.thinkfree.drive.partition;

public interface PartitionKeyProvider {
    Integer getSharedByUserSeq();
    Long getResourceSeq();
}
