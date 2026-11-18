package com.thinkfree.drive.postprocess.orchestrator.dto;

import java.time.Instant;
import java.util.Collection;

public record PostProcessShareTargetChangeDto
(
    Integer userSeq,
    String userName,
    Long resourceSeq,
    String fileName,
    Collection<Integer> addedTargetUserSeqList,
    Collection<Integer> deletedTargetUserSeqList,
    Integer actorUserSeq,
    Instant occurredDatetime,
    Integer tenantSeq
)
{
}
