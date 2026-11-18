package com.thinkfree.drive.postprocess.orchestrator.dto;

import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.resource.enums.ResourceType;

import java.time.Instant;
import java.util.List;

public record PostProcessTrashDto
(
    Integer userSeq,
    String userName,
    List<ResourceBrief> resourceBriefs,
    ResourceType resourceType,
    Integer actorUserSeq,
    Instant occurredDatetime,
    Integer tenantSeq
)
{
}
