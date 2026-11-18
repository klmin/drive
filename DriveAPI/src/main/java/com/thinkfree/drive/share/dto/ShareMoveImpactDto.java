package com.thinkfree.drive.share.dto;

import com.thinkfree.drive.resource.enums.ResourceType;

public record ShareMoveImpactDto
(
    Integer sourceUserSeq,
    Long sourceResourceSeq,
    Integer destinationUserSeq,
    Long destinationResourceSeq,
    ResourceType resourceType,
    Boolean isDelete
)
{
}
