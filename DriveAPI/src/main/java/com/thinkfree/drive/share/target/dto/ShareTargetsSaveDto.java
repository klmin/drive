package com.thinkfree.drive.share.target.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ShareTargetsSaveDto {
    private ShareType shareType;
    private ResourceType resourceType;
    private List<ShareTargetSaveDto> targets;
}
