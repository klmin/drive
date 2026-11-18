package com.thinkfree.drive.share.dto;

import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ShareUpdateDto implements PermissionHolder {
    
    private Long shareSeq;
    private Integer userSeq;
    private Integer ownerTenantSeq;
    private ShareType shareType;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    private ShareType previousShareType;
    private ResourceType resourceType;
    private Long resourceSeq;
    private String resourceName;
    private ShareTargetsReplaceDto targetsReplaceDto;
}
