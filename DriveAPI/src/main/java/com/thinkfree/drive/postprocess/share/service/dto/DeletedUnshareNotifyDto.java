package com.thinkfree.drive.postprocess.share.service.dto;

import com.thinkfree.drive.resource.contract.ResourceBrief;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class DeletedUnshareNotifyDto {
    private Integer userSeq;
    private String userName;
    private List<ResourceBrief> resourceBriefs;
    private ResourceType resourceType;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Integer tenantSeq;
    private String tenantName;
}
