package com.thinkfree.drive.resource.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceMoveDto {
    private Long parentFolderSeq;
    private Long resourceSeq;
    private ResourceType resourceType;
    private Boolean isDelete;
    private Integer userSeq;
    private Integer targetUserSeq;
}
