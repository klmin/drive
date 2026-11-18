package com.thinkfree.drive.postprocess.orchestrator.dto;

import java.time.Instant;

public record PostProcessQuotaChangeDto
(
    Integer userSeq,
    Integer actorUserSeq,
    Instant occurredDatetime
)
{
}
