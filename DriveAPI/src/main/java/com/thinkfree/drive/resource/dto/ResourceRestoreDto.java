package com.thinkfree.drive.resource.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceRestoreDto {
    private Long resourceSeq;
    private ResourceType resourceType;
    private Integer userSeq;
    private boolean parentExists;
}
