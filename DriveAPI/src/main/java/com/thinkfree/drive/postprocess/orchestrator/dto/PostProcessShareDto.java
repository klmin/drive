package com.thinkfree.drive.postprocess.orchestrator.dto;

import java.time.Instant;
import java.util.Collection;

public record PostProcessShareDto
(
    Integer userSeq,
    String userName,
    Long resourceSeq,
    String fileName,
    Collection<Integer> targetUserSeqList,
    Integer actorUserSeq,
    Instant occurredDatetime,
    Integer tenantSeq
)
{
}
