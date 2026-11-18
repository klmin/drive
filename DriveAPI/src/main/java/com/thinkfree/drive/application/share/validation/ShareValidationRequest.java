package com.thinkfree.drive.application.share.validation;

import com.thinkfree.drive.share.enums.ShareType;

import java.util.List;

public interface ShareValidationRequest {

    ShareType shareType();
    Boolean canView();
    Boolean canEdit();
    Boolean canDownload();
    Boolean canUpload();
    Boolean canShare();
    List<? extends ShareTargetValidationItem> targets();

}
