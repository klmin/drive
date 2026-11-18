package com.thinkfree.drive.resource.dto;

import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResourceRenameDto {
    
    private Long resourceSeq;
    private Integer userSeq;
    private ResourceType resourceType;
    private String resourceName;
    private String originalFileName;
    private Boolean isDelete;
    private Integer copyNumber;
    private String baseFileName;
    private boolean copyFile;
    private String originalFileNameWithoutExt;
    private String ext;
    private NameConflictPolicy nameConflictPolicy;
    
    
    
}
