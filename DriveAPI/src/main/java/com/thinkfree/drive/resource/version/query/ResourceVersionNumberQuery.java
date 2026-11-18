package com.thinkfree.drive.resource.version.query;

import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ResourceVersionNumberQuery {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Integer versionNumber;
    private Integer tempOffset;
    private ResourceVersionStatus status;
    private Integer updateUserSeq;
    private Instant updateDate;
    
}
