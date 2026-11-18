package com.thinkfree.drive.trash.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TrashSaveDto {
    
    private Integer userSeq;
    private Long resourceSeq;
    
}
