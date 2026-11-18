package com.thinkfree.drive.postprocess.share.service.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class UnshareLookupNotifyDto {
    private Integer userSeq;
    private Integer targetUserSeq;
    private String userName;
    private String fileName;
    private List<Long> resourceSeqList;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private ResourceType resourceType;
    private Integer tenantSeq;
    private String tenantName;

}
