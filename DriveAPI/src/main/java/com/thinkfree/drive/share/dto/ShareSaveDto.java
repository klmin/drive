package com.thinkfree.drive.share.dto;

import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.dto.ShareTargetSaveDto;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Builder
@Getter
public class ShareSaveDto implements PermissionHolder {
    private Integer userSeq;
    private Long resourceSeq;
    private ResourceType resourceType;
    private ShareType shareType;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    private String password;
    private Boolean isPasswordEnabled;
    private Instant expireDate;
    private List<ShareTargetSaveDto> targets;
    
}
