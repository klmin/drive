package com.thinkfree.drive.postprocess.orchestrator.dto;

import com.thinkfree.drive.postprocess.enums.MoveContextType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareCleanupAction;

import java.time.Instant;
import java.util.List;

public record PostProcessMoveDto
(
    Integer userSeq,
    Integer targetUserSeq,
    Integer actorUserSeq,
    String userName,
    String fileName,
    List<Long> resourceSeqList,
    Instant occurredDatetime,
    Long totalSizeByte,
    MoveContextType moveContextType,
    ShareCleanupAction shareCleanupAction,
    ResourceType resourceType,
    Integer tenantSeq
)
{
}
