package com.thinkfree.drive.resource.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceUpdateDto {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Long sizeByte;
    private Integer updateUserSeq;
    
}
