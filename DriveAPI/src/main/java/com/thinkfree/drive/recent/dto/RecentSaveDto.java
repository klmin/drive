package com.thinkfree.drive.recent.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class RecentSaveDto {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private Instant updateDate;
}
