package com.thinkfree.drive.partition;

import java.util.List;

public record UserResourceGroup
(
    Integer userSeq,
    List<Long> resourceSeqList
)
{
}
