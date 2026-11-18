package com.thinkfree.drive.permission.data;

import com.thinkfree.drive.permission.support.ResourcePermissionAccessor;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SharePermissionData implements ResourcePermissionAccessor {

    private Integer userSeq;
    private Long resourceSeq;

    private Long shareSeq;
    private Long shareResourceSeq;
    private ShareType shareType;
    private Boolean shareCanView;
    private Boolean shareCanEdit;
    private Boolean shareCanDownload;
    private Boolean shareCanUpload;
    private Boolean shareCanDelete;
    private Boolean shareCanShare;
    private Integer shareDepth;

    private Long shareTargetShareSeq;
    private Long shareTargetSeq;
    private Long shareTargetShareResourceSeq;
    private ShareType shareTargetShareType;
    private Boolean shareTargetCanView;
    private Boolean shareTargetCanEdit;
    private Boolean shareTargetCanDownload;
    private Boolean shareTargetCanUpload;
    private Boolean shareTargetCanDelete;
    private Boolean shareTargetCanShare;
    private Integer shareTargetDepth;

}
