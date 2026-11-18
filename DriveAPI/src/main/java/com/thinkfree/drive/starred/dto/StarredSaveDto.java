package com.thinkfree.drive.starred.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StarredSaveDto {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
}
