package com.thinkfree.drive.share.target.dto;

import com.thinkfree.drive.permission.support.PermissionHolder;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ShareTargetUpsertDto implements PermissionHolder {
    private Integer userSeq;
    private Long shareSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private Instant shareDate;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
}
