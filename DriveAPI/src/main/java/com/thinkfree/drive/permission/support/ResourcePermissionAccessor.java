package com.thinkfree.drive.permission.support;

import com.thinkfree.drive.share.enums.ShareType;

public interface ResourcePermissionAccessor {
    
    Integer getUserSeq();
    Long getResourceSeq();
    
    Long getShareSeq();
    Long getShareResourceSeq();
    ShareType getShareType();
    Boolean getShareCanView();
    Boolean getShareCanEdit();
    Boolean getShareCanDownload();
    Boolean getShareCanUpload();
    Boolean getShareCanDelete();
    Boolean getShareCanShare();
    Integer getShareDepth();
    
    Long getShareTargetShareSeq();
    Long getShareTargetSeq();
    Long getShareTargetShareResourceSeq();
    ShareType getShareTargetShareType();
    Boolean getShareTargetCanView();
    Boolean getShareTargetCanEdit();
    Boolean getShareTargetCanDownload();
    Boolean getShareTargetCanUpload();
    Boolean getShareTargetCanDelete();
    Boolean getShareTargetCanShare();
    Integer getShareTargetDepth();
    
}
