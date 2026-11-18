package com.thinkfree.drive.resource.result;

import com.thinkfree.drive.resource.contract.ResourceBrief;

import java.time.Instant;
import java.util.List;

public record ResourceDeleteResult
(
        List<Long> deleteResourceSeqList,
        List<String> deleteKeys,
        long totalSizeByte,
        Instant occurredDatetime,
        List<ResourceBrief> resourceBriefs
)
{
}
