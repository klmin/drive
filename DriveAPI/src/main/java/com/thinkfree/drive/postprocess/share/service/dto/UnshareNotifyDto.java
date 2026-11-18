package com.thinkfree.drive.postprocess.share.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class UnshareNotifyDto {
    private Integer userSeq;
    private Integer targetUserSeq;
    private String userName;
    private String fileName;
    private Long resourceSeq;
    private List<Integer> targetUserSeqList;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Integer tenantSeq;
    private String tenantName;

}
