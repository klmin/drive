package com.thinkfree.drive.postprocess.share.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ShareNotifyDto {
    private Integer userSeq;
    private String userName;
    private Long resourceSeq;
    private String fileName;
    private List<Integer> targetUserSeqList;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Integer tenantSeq;
    private String tenantName;
}
