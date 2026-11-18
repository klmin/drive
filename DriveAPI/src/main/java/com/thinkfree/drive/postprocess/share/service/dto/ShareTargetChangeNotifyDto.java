package com.thinkfree.drive.postprocess.share.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;

@Getter
@Builder
public class ShareTargetChangeNotifyDto {
    private Integer userSeq;
    private String userName;
    private Long resourceSeq;
    private String fileName;
    private Collection<Integer> addedTargetUserSeqList;
    private Collection<Integer> deletedTargetUserSeqList;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Integer tenantSeq;
    private String tenantName;
}
