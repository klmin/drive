package com.thinkfree.drive.share.target.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareTargetsReplaceDto {
    private Long shareSeq;
    private Integer sharedByUserSeq;
    private ShareType shareType;
    private ResourceType resourceType;
    private Long resourceSeq;
    private List<ShareTargetUpsertDto> targets;
}
