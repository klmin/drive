package com.thinkfree.drive.resource.version.result;

import java.util.List;

public record ResourceVersionStatsResult
(
    List<Long> resourceVersionSeqList,
    long totalSizeByte
)
{
}
